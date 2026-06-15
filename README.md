# Albedo - Guardian Overseer

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1-blue)]()

Minecraft 26.1 Fabric 模组，添加《不死者之王》(Overlord) 中的雅儿贝德作为史诗 Boss 战。

## 召唤方式

将**下界之星**丢到**下界合金块**上，片刻后雅儿贝德将被召唤，下界之星和合金块同时被消耗。

## Boss 属性

| 属性 | 值 |
|------|-----|
| 生命值 | 400 |
| 攻击力 | 18 |
| 盔甲 | 40 |
| 移动速度 | 0.32 |
| 击退抗性 | 100% |
| 追踪范围 | 48 格 |
| 弹射物免疫 | 是 |

## 三阶段系统

| 阶段 | HP 范围 | 特性 |
|------|---------|------|
| P1 黑色守护者 | 400–250 | 近战横扫 + 格挡姿态，攻击慢但伤害高 |
| P2 地狱深渊 | 249–130 | 戟形态变换，解锁突刺 + 斩击波 |
| P3 魅魔真容 | 129–0 | 装甲碎裂，速度提升，解锁分身 + AOE |

## 技能

1. **横扫千军** — 180° 扇形攻击，击退 + 16 伤害，P1 起
2. **地狱突刺** — 直线冲锋 6 格，22 穿甲伤害，P2 起
3. **深渊斩击波** — 发射 3 道弧形粒子波，每道 12 伤害，P2 起
4. **暗影分身** — 召唤 2 个幻影，存活 8 秒，P3 起
5. **终焉裁决** — 跃起砸地，半径 5 格 AOE 25 伤害 + 失明，P3 起

## 掉落物

| 物品 | 概率 | 说明 |
|------|------|------|
| 魅魔之角 | 100% | 头盔，+40 护甲，夜视 + 力量 + 伤害吸收 III |
| 守护者碎片 | 100% | 3–5 个，合成材料 |
| 地狱深渊 | 25% | 下界合金斧，攻击 +8，横扫范围 +2 |

## 互动系统

右键雅儿贝德循环切换状态：

- **跟随** — 跟随玩家，距离过远自动瞬移
- **巡逻** — 在当前位置游走，攻击 12 格内敌对生物
- **待机** — 静止不动，不攻击不移动

## 合成配方

- **附魔金苹果**：8 个守护者碎片 + 1 个金苹果 → 64 个附魔金苹果

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/) 0.18.4+
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api) 0.145.1+
3. 安装 [GeckoLib](https://modrinth.com/mod/geckolib) 5.5+
4. 下载 [albedo-1.0.0.jar](https://github.com/gggiz/albedo-mod/releases/latest) 放入 `mods` 文件夹

## 命令

```
/summon albedo:albedo
```

## 配置

编辑 `config/albedo.properties`（首次运行后自动生成），可调整所有 Boss 属性、技能冷却和伤害数值。

## 构建

```bash
./gradlew build
```

要求 Java 25 + Gradle 9.4+。

## 致谢

- 角色出自丸山くがね《不死者之王》(Overlord)
- GeckoLib 动画引擎
- Blockbench 建模工具

## 许可

MIT License
