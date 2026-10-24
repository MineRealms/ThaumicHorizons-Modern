# Thaumic Horizons（神秘视界）— Unofficial Port

把 1.12.2 / Thaumcraft 6 的 **Isorropia（kedition）** 移植到 **Minecraft 1.20.1 Forge 与 1.21.1 NeoForge**，
运行在 **Thaumcraft 4 Reborn（TC4R）** 上。玩法基线是 1.12 的 Isorropia；TC4 机制
（精华、注魔、扫描、多方块、法杖交互）以它的前身 **Thaumic Horizons（1.7.10，TC4）** 为准。

```text
当前版本：1.0.3-alpha        mod id：isorropia
1.20.1：Forge 47.x（[47,48)）/ Java 17   ·   1.21.1：NeoForge / Java 21
必需依赖：TC4R 0.1.0-20740（运行版本 4.2.3.5-*-port.0.1.0-20740）、Curios
```

## 玩法介绍

本模组在 TC4R 的基础上补全了 Thaumic Horizons 的核心体验，围绕「生命改造」与「位面工程」两条主线展开：

### 生物注魔（治疗缸）
搭建**治疗缸多方块**，将捕获的生物沉入缸中，按配方灌注要素，把它改造成全新的生物。
共 **19 种生物注魔**（从元素亲和到活体傀儡），全部有独立 AI、属性与渲染。

### 自我注魔
治疗缸同样可以处理你自己：**10 种自我注魔**改造玩家的身体，
配合**躯体大脑**与透镜系统（envy 透镜等）实现植入与强化，研究树与按键/HUD 完整。

### TH 焦点线
移植了 Thaumic Horizons 的五个专属焦点：**照明、液化、分解、收容、活化**，
各带升级槽（nuggets / purity / enervation / berserk / detonation 等），
活化焦点可驱动 **EntityAnimatedBlock**（方块活体）。

### 能量与节点机械
- **魔力发电机 / 精华发电机**：把 vis 与 essentia 转化为能量；
- **节点监视器 / 合成节点 / 以太碎片**：读数、扩容与网络充能；
- **转导放大器**：稳定器在下、换能器在上的合法结构把节点**充能化**（每要素 +10/-10），
  放大器侧挂供能，拆散结构会把节点炸成 flux。

### 位面工程（Planar 线）
`位面导管 → 漩涡衰减器（六束封印 rift） → 重组器（双节点融合） → 位面漩涡 → 口袋位面`。
成熟的漩涡是**饥饿节点**：吞噬方块、撕扯实体，六束封印后丢入**原初珍珠**即可开辟**口袋位面**
（分层地形、洞穴、生物群系、成对传送门），用**钥匙石/钥匙孔/传送门框**往返。
安装 GregTech Modern 时自动启用 GTM 材料的钥匙石变体配方。

### 虚空锻造
向成熟漩涡投喂物品：虚空锭 → **虚空腻子**（修复耐久）、盐精 → 智慧灵珠（wisp）、
**惰性水晶杖芯** → 满充能**水晶法杖**。产物流封存在 rift 中，用法杖右键漩涡即可取出。

### 血液注魔
**注射器**抽血、**血清转化器**按要素亲和度权重表转化血清（10 槽 + 拨盘协议 + TH 原版 GUI）、
**爆裂小瓶**投掷产物。

### 工作傀儡
TH 式**工作傀儡**：独立 AI，右键安装 TC4R 核心与升级（每槽 4 位压缩存储），
配合傀儡粉末可从虚空裂隙生成虚空傀儡。

### 兼容
- **Forbidden Magic**：安装 FM 时自动复用其七宗罪要素定义，双方案配方/研究切换；
- **JEI**：生物/自我注魔实时页面；
- **KubeJS**：`ThaumicHorizons` 绑定，可用脚本直接增改注魔配方。

## 构建与开发

```bash
./gradlew.bat :1.20.1-forge:build       # 1.20.1 节点：编译 + JUnit
./gradlew.bat :1.21.1-neoforge:build    # 1.21.1 节点
python tools/validate_tc_data.py        # TC 数据静态校验（研究/配方/要素/语言键）
python tools/client_probe.py start      # 自动开客户端进探针世界并连上 Rosetta 桥
                                        #   ISORROPIA_ADDONS=1  挂载 TC4R 附属运行时
                                        #   ISORROPIA_COMPAT=1  附加 JEI/KubeJS
python tools/client_sweep.py            # 同一会话跑完全部客户端探针并逐项判定
```

多版本基于 Stonecutter：`src/` 为规范源码树，节点差异用 stonecutter 条件与平台 shim 表达，
`versions/<节点>/gradle.properties` 承载节点坐标。

TC4R 是用户提供的本地构建，**不入库**：把 `local-repo/dev/tc4port/thaumcraft-forge/0.1.0-20740/{jar,pom}`
从相邻工程（如 `../Technomancy-1.20.1/local-repo`）复制过来即可，或用 `-Ptc4Repository=<maven 根>` 指向别处。

## 当前进度

- **S0–S7（原 Isorropia 范围）全部完成并实机验证**：治疗缸/生物与自我注魔、透镜与躯体大脑、
  灵魂信标/改造矩阵/收容焦点/变形手指、客户端特效收尾（31 客户端探针 + 6 服务端探针全绿）。
- **Thaumic Horizons 扩展全部移植完成，backlog 已清空**：五个 TH 焦点、能量与节点机械、
  转导放大器、位面/漩涡/口袋位面/成对传送门、虚空锻造、血液注魔与血清转化器、工作傀儡、
  合成节点/以太碎片，均经实机探针验证。
- **1.21.1 NeoForge 多版本完成**：双节点构建、JEI/KubeJS 兼容层、CI（见
  `.github/workflows/build.yml`）。

细表与逐阶段偏差见 [docs/PORT_PLAN.zh-CN.md](docs/PORT_PLAN.zh-CN.md)。

## 文档索引

| 文件 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 交接入口：构建、目录、当前该做什么 |
| [docs/PORT_PLAN.zh-CN.md](docs/PORT_PLAN.zh-CN.md) | 来源与目标、已定决策、阶段表、每阶段与上游的偏差 |
| [docs/VALIDATION.zh-CN.md](docs/VALIDATION.zh-CN.md) | **验证方法**：服务端/客户端探针、截图、网格预览、数据校验；实机抓到的缺陷清单 |
| [docs/RENDERING.zh-CN.md](docs/RENDERING.zh-CN.md) | 渲染与特效：已实现项、与规格的对应、未完成项 |
| [docs/render-spec/](docs/render-spec/) | 逐区域渲染规格（00 共享 API 参考 + 01–07 分区域），由 1.7.10/1.12 源码逐行分析得出 |
| [docs/MULTIVERSION-BUILD-PLAN.zh-CN.md](docs/MULTIVERSION-BUILD-PLAN.zh-CN.md) | 多版本（Stonecutter）构建方案 |

## 目录

```text
src/               共享源码（canonical；stonecutter 条件承载平台差异）
  common/          注册、方块/物品/实体、治疗缸与注魔、透镜、capability、网络、兼容桥
  client/          渲染器（vat/matrix/jar/beacon）、实体与 curio 渲染、HUD、FX、按键
versions/          1.20.1-forge / 1.21.1-neoforge 节点（节点属性与构建输出）
src/main/resources/
  assets/isorropia/  方块/物品模型、贴图、语言、音效、OBJ（vat_redo.obj）
  data/isorropia/    研究 JSON、坩埚/奥术/注魔配方、注魔数据、要素、curios 槽位、战利品表
probes/            实机探针（server/ 与 client/），配合 Rosetta 调试桥使用
tools/             构建与校验脚本：数据校验、资源导入、注魔数据生成、网格预览、客户端探针
docs/              计划、验证、渲染文档与渲染规格
local-repo/        TC4R 与附属的本地 maven 仓库（jar 本体不入库）
```

## 授权

原 Isorropia 与 Thaumic Horizons 的代码/素材版权归各自作者（`mods.toml` 中 `mod_license` 为 All Rights Reserved）。
本仓库是移植工作区，不含 TC4R 本体。
