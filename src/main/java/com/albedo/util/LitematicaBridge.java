package com.albedo.util;

import com.albedo.AlbedoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class LitematicaBridge {
    private static final Logger LOG = LoggerFactory.getLogger("Albedo/Litematica");
    private static boolean checked;
    private static boolean available;

    public static boolean isAvailable() {
        if (!checked) {
            checked = true;
            available = FabricLoader.getInstance().isModLoaded("litematica");
            if (available) {
                LOG.info("Litematica 已检测到");
            } else {
                LOG.warn("Litematica 未安装");
            }
        }
        return available;
    }

    /**
     * 获取 Litematica 的虚拟投影世界。
     */
    public static Level getSchematicWorld() {
        if (!isAvailable()) return null;
        try {
            Class<?> handlerClass = Class.forName("fi.dy.masa.litematica.world.SchematicWorldHandler");
            Method m = handlerClass.getMethod("getSchematicWorld");
            Level world = (Level) m.invoke(null);
            if (world == null) {
                LOG.warn("SchematicWorldHandler.getSchematicWorld() 返回 null，可能没有加载投影");
            }
            return world;
        } catch (Exception e) {
            LOG.error("获取投影世界失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前选中投影在世界中的包围盒。
     * 尝试多种反射路径以兼容不同 Litematica 版本。
     */
    public static AABB getPlacementBounds() {
        if (!isAvailable()) return null;
        try {
            // DataManager.getInstance().getSchematicPlacementManager().getSelectedSchematicPlacement()
            Class<?> dmClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object dm = dmClass.getMethod("getInstance").invoke(null);
            Object pm = dmClass.getMethod("getSchematicPlacementManager").invoke(dm);
            Object placement = pm.getClass().getMethod("getSelectedSchematicPlacement").invoke(pm);
            if (placement == null) {
                LOG.warn("没有选中投影 (getSelectedSchematicPlacement 返回 null)");
                return null;
            }

            BlockPos origin = (BlockPos) placement.getClass().getMethod("getOrigin").invoke(placement);
            LOG.info("投影原点: {}", origin);

            // 获取 schematic 对象
            Object schematic = placement.getClass().getMethod("getSchematic").invoke(placement);

            // 方法1: getSubRegionBoxes() — 最精确，返回世界坐标下的包围盒
            try {
                Method getBoxes = placement.getClass().getMethod("getSubRegionBoxes");
                Object boxes = getBoxes.invoke(placement);
                Object box = null;
                if (boxes instanceof Map<?,?> map && !map.isEmpty()) {
                    box = map.values().iterator().next();
                } else if (boxes instanceof List<?> list && !list.isEmpty()) {
                    box = list.get(0);
                }
                if (box != null) {
                    BlockPos min, max;
                    try {
                        Method getMin = box.getClass().getMethod("getMin");
                        Method getMax = box.getClass().getMethod("getMax");
                        min = (BlockPos) getMin.invoke(box);
                        max = (BlockPos) getMax.invoke(box);
                    } catch (NoSuchMethodException e) {
                        // Litematica 0.27.x: Box 使用 getPos1/getPos2
                        Method getPos1 = box.getClass().getMethod("getPos1");
                        Method getPos2 = box.getClass().getMethod("getPos2");
                        BlockPos p1 = (BlockPos) getPos1.invoke(box);
                        BlockPos p2 = (BlockPos) getPos2.invoke(box);
                        min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
                        max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
                    }
                    AABB result = new AABB(min.getX(), min.getY(), min.getZ(),
                            max.getX() + 1, max.getY() + 1, max.getZ() + 1);
                    LOG.info("通过 getSubRegionBoxes 获取边界: {} → {}", min, max);
                    return result;
                }
            } catch (Exception e) {
                LOG.warn("getSubRegionBoxes 失败: {}", e.getMessage());
            }

            // 方法2: getAreaPositions() / getAreaSizes() — 从 schematic 获取子区域偏移和大小
            try {
                Method getPositions = schematic.getClass().getMethod("getAreaPositions");
                Method getSizes = schematic.getClass().getMethod("getAreaSizes");
                List<?> positions = (List<?>) getPositions.invoke(schematic);
                List<?> sizes = (List<?>) getSizes.invoke(schematic);
                if (positions != null && !positions.isEmpty() && sizes != null && !sizes.isEmpty()) {
                    BlockPos areaPos = (BlockPos) positions.get(0);
                    Object szObj = sizes.get(0);
                    int sx = (int) szObj.getClass().getMethod("getX").invoke(szObj);
                    int sy = (int) szObj.getClass().getMethod("getY").invoke(szObj);
                    int sz = (int) szObj.getClass().getMethod("getZ").invoke(szObj);
                    AABB result = new AABB(
                            origin.getX() + areaPos.getX(), origin.getY() + areaPos.getY(), origin.getZ() + areaPos.getZ(),
                            origin.getX() + areaPos.getX() + sx, origin.getY() + areaPos.getY() + sy, origin.getZ() + areaPos.getZ() + sz);
                    LOG.info("通过 getAreaPositions/getAreaSizes 获取边界: area={}, size={}x{}x{}", areaPos, sx, sy, sz);
                    return result;
                }
            } catch (Exception e) {
                LOG.warn("getAreaPositions/getAreaSizes 失败: {}", e.getMessage());
            }

            // 方法3: getMetadata().getEnclosingSize() — 最后备选，但可能包含子区域间的空隙
            try {
                Object metadata = schematic.getClass().getMethod("getMetadata").invoke(schematic);
                Object size = metadata.getClass().getMethod("getEnclosingSize").invoke(metadata);
                int sx = (int) size.getClass().getMethod("getX").invoke(size);
                int sy = (int) size.getClass().getMethod("getY").invoke(size);
                int sz = (int) size.getClass().getMethod("getZ").invoke(size);
                AABB result = new AABB(
                        origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + sx, origin.getY() + sy, origin.getZ() + sz);
                LOG.info("通过 getMetadata().getEnclosingSize() 获取边界: {}x{}x{} (注意：可能包含子区域间的空隙)", sx, sy, sz);
                return result;
            } catch (Exception e) {
                LOG.warn("getMetadata().getEnclosingSize() 失败: {}", e.getMessage());
            }

            LOG.warn("所有获取投影边界的方法均失败，请检查投影是否已正确加载");
            return null;
        } catch (Exception e) {
            LOG.error("获取投影边界异常: {}", e.getMessage());
            return null;
        }
    }
}
