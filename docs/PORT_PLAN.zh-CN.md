# Isorropia 1.20.1 移植计划

## 来源与目标

| 角色 | 路径 | 说明 |
|---|---|---|
| 移植对象（功能基线） | `../Isorropia_kedition` | 1.12.2-kedition-2.1.4，TC6 API，154 个 Java 文件 |
| 机制参考（TC4 原型） | `../ThaumicHorizons` | GTNH 1.7.10 版 Thaumic Horizons，Isorropia 的前身；TC4 机制（精华、注魔、扫描）以它为准 |
| 目标 API | TC4R `dev.tc4port:thaumcraft-forge:0.1.0-20732` | 运行版本 `4.2.3.5-1.20.1-port.0.1.0-20732`；源码参考 `reference/tc4r-20721-sources`（20721，git 忽略） |
| 构建/工具参考 | `../Technomancy-1.20.1` | 同一 TC4R 的成熟移植工程（local-repo、探针、GameTest 套路） |
| 插件写法参考 | `../_tc4_1201/forbidden-magic-*.jar` | TC4R 生态里的附属 mod：要素/研究/实体要素 datapack 写法 |

范围：最初 S0–S7 为 Isorropia 已有功能；后续按用户要求扩展到 GTNH Thaumic Horizons 中 Isorropia 没有承接的内容（下文 TH 扩展分组）。原 S0–S7 完成不代表 TH 全覆盖。Isorropia 中依赖 TC6 独有机制（光环区块 vis、knowledge 点数、研究 stage）的地方，按 Thaumic Horizons 的 TC4 做法落到 TC4R。

## 已定决策

1. **工程**：Forge 47.4.23 / 官方映射 / Java 17；TC4R 精确锁定 20732；Curios 5.14.1（TC4R 必需，且躯体大脑是 Curios 头部饰品）。Gradle 代理在 `~/.gradle/gradle.properties`（127.0.0.1:7890），不进工程。
2. **包与 ID**：包根 `fr.wind_blade.isorropia`，mod id `isorropia`，注册名沿用原版 1.12 的名字。
3. **要素**：`fames`/`corpus` 是 TC4 原生要素，直接用 `thaumcraft:fames`/`thaumcraft:corpus`；七个罪孽要素定义为 `isorropia:*`（`data/isorropia/thaumcraft/aspects/<name>.json`）。已知重叠：Forbidden Magic 定义了同名的 `forbidden_magic:*`，两者同装会出现同名不同 ID 的要素。TC6→TC4 的要素换算集中在 `common/Aspects`。
4. **研究**：TC6 研究 JSON（stage/knowledge）改写为 TC4R 格式 `data/isorropia/thaumcraft/research/isorropia.json`，类别 `isorropia:isorropia`，key 带命名空间。
5. **配方**：全部改为 datapack JSON（`thaumcraft:crucible` / arcane / `thaumcraft:infusion` 等 TC4R serializer），不保留 Java 注册。
6. **砍掉**：Thaumic Bases 联动（烟鬼乌贼 + `tb_compat.json`，1.20.1 无 TB）；贪食/色欲透镜（原版未注册的死代码）；Saehrimnir 系列（原版未注册）。
7. **贴图位置**：TC4R 会把所有命名空间的 `textures/models/` 拼进方块图集，所以渲染器皮肤一律放 `textures/entity/`；物品模型用的图标放 `textures/item/`。

## 阶段

| 阶段 | 内容 | 状态 |
|---|---|---|
| S0 | MDK 骨架、TC4R/Curios 依赖、git | 完成 |
| S1 | 方块/物品/音效/创造栏注册，资源迁移（贴图改路径、lang→json、blockstate/模型重写），原版配方、战利品表 | 完成（复杂物品先占位，随所属阶段替换） |
| S2 | TC 数据：要素、物品/实体要素、研究树、坩埚/奥术/注魔配方 | 完成（`python tools/validate_tc_data.py` 静态校验通过；加载需实机确认） |
| S3 | 14 个实体 + AI + 属性 + 渲染器/模型 | 完成（偏差见下） |
| S4 | 治疗缸多方块 + 生物注魔/自我注魔 + 能力（capability）+ 网络包 | 完成（偏差见下） |
| S5 | 透镜系统 + 躯体大脑（Curios）+ 按键 + HUD | 完成（透镜行为/轮盘/世界叠加/HUD 读数/躯体大脑落点均在 DEV 客户端实机验证，见下） |
| S6 | 改造矩阵、灵魂罐、灵魂信标、收容焦点、变形手指 | 完成（均实机验证，偏差见下） |
| S7 | 客户端特效与渲染收尾；集中实机验证 | 完成：TC6 `FXGeneric` 移植为 `GenericFx`，天穹吸收/通量爆炸/矩阵排气/守墓者光束按原参数重建；删除无上游依据的替身闪光；`tools/client_sweep.py` 31 个客户端探针与服务端探针 01–06 全部通过（见 RENDERING、VALIDATION） |

每个阶段：实现 → `gradlew build` → 修复 → 提交。实机测试（runServer/runClient/探针）集中在批次末尾一次做。

## 渲染与特效（跨阶段）

渲染这块单独成篇，因为它是「不跑客户端就发现不了」的重灾区：

- 逐区域**规格**在 [render-spec/](render-spec/)（00 是共享的 TC4R/Forge 渲染参考，01–07 是 vat/matrix/灵魂罐与信标/粒子特效/实体审计/透镜与 HUD/物品模型），
  每条都带 1.7.10 与 1.12 的行号、精确几何与混合状态、以及「该照哪个上游」的判断。
- **现状**（做了什么、与规格的差在哪、客户端验证的硬限制）见 [RENDERING.zh-CN.md](RENDERING.zh-CN.md)。
- 截图默认只有世界（探针把 `hideGui` 置真）；加 `--gui` 或用 `guishot` 时 HUD 与界面也进图，可以直接按像素验收。

## 并行开发方式

S2、S3 在独立 worktree 分支上并行（`../Isorropia-wt/s2-data`、`../Isorropia-wt/s3-entities`），完成后合入 master。共享的基础设施（`IsorropiaAspects`、`IsorropiaNetwork`）先在 master 落地，避免各分支各写一份。

## S2 数据说明

文件：研究 `data/isorropia/thaumcraft/research/isorropia.json`（38 条，类别 `isorropia:isorropia`）；配方 `data/isorropia/recipes/{crucible,arcane,infusion}/`（17 坩埚 + 1 奥术 + 8 注魔，配方 ID 如 `isorropia:crucible/wheat_growth`）；物品要素 `object_aspects/isorropia.json` + `vanilla_sins.json`；实体要素 `entity_aspects/isorropia.json`。改数据后跑 `python tools/validate_tc_data.py`。

**物品替换（TC6 → TC4R）**
- 要素水晶：火/风/秩序 → `thaumcraft:fire_shard`/`air_shard`/`order_shard`；嫉妒水晶 → 带 `isorropia:invidia` 的 `thaumcraft:crystal_essence`（`thaumcraft:components` 原料）。
- 书写工具 → `inkwell`；黄铜板 → `minecraft:iron_ingot`；生物奥术心智 → `zombie_brain`；注魔矩阵 → `runic_matrix`；vis 共鸣器 → `resonator`（源质共鸣器）；虚空锭/虚空罐/脑罐/大脑 → `void_ingot`/`void_jar`/`brain_jar`/`zombie_brain`。
- 基础透镜奥术配方：TC6 的 20 vis + 六种水晶各 1 → 六种初始要素各 5 vis。注魔要素量沿用 TC6 原值，未缩放。

**研究改写**
- TC6 stage/knowledge 改为 TC4 `aspects` + `complexity`（数值参照 Thaumic Horizons 同类研究）；TC6 meta：HIDDEN→CONCEALED，SPIKY→SPECIAL，HEX/REVERSE 丢弃。
- 扫描要求改为触发器（条目加 HIDDEN，扫到任意一个即发现，不再要求全部）：动物→创造物注魔，腐化生物→治疗缸，鸡/牛/猪/猪人/末影人/铁傀儡/村民→Similitudo，羊→自剪毛，兔/史莱姆→果冻兔，可驯服生物→灌注忠诚，`!scan.sun`→`lux` 要素触发（火透镜），`!scan.taintsrc`→腐化方块触发（噬腐猪），`!ignis`→`ignis` 要素触发（地狱犬）。
- 丢弃：`required_item`/`required_craft`、`!experiment.villager_zombie`（治疗缸）、`m_hellandback`（地狱犬）、治疗缸附录（无文本）、父研究 MINDBIOTHAUMIC。
- 父研究映射：BASEALCHEMY→CRUCIBLE，BASEARTIFICE→GOGGLES，WARDEDJARS→JARLABEL，BASEELDRITCH→隐藏父 ELDRITCHMINOR，`f_TELEPORT`→隐藏父 FOCUSPORTABLEHOLE；透镜注魔另加隐藏父 INFUSION。
- 生物注魔/自我注魔（治疗缸里做，S4 实现）只有文字页；治疗缸多方块的蓝图页（COMPOUND_CRAFTING + 成型 handler）留到 S4。
- 要素：TC4R 食物已自带 fames/corpus，Isorropia 补的 HUNGER/FLESH 规则不再需要；高饱和食物加 gula 改成原版食物静态表；原版物品的罪孽要素放在 `vanilla_sins.json`，装了 Forbidden Magic 时不加载（同原版行为）。实体为 `required: false`，S3 注册实体后生效。
- 语言：旧 TC6 `research.*`（含扫描提示）全部删除，`<PAGE>` 拆成多页，`<BR>`/`<LINE>` 保留（TC4R 支持）。研究背景转为 512×512 PNG。

### S3 与上游的偏差

- 守墓猫、守护黑豹以 `Cat` 为基类（1.20.1 只有猫可驯服），用猫模型；黑豹恒为驯服、无主人时只是中立护卫，主人留给 S4 注魔 `tame(player)`；皮肤生成时随机 guardian1–3（上游实际恒为 guardian1）。
- 羊蛛：类别改为 CREATURE，不在和平/远离时消失、不妨碍睡觉、生成时去掉骷髅骑手与药水效果。
- 腐化猪：吃 `thaumcraft:taint` 标签方块（腐化土/草→泥土，结壳腐化→石头，其余→空气），按 TH 净化该列生物群系；TC4R API 无净化接口，唯一的内部调用集中在 `common/compat/TaintBridge`。目标改为 `thaumcraft:taint_spider`。
- 果冻兔：TC4 无区块灵气，生长不再抽 vis（上游本就不看抽取结果）；vitium 兔改为 `FluxApi.consumeNearby` 吸收附近通量。剥一层后 20 tick 无敌，防止火焰/仙人掌每 tick 剥层。ignis 兔名称键改为能命中的 `bunnignis`。
- 矿猪：矿物表由 `forge:ores/*` 与 `forge:nuggets/*`（无粒则 `forge:dusts/*`）推导，每种材料一条；主矿/其他权重进 `isorropia-common.toml`（默认 1/1 同上游）。`taint_eat_cooldown` 同在该文件。
- 守墓猫的圣光束：早期用精华流光代替，S7 已换成 TC4R 运行版公开的 `spawnBeam`（TC4 `FXBeam`，照 TH 参数，见 RENDERING）。刷怪蛋配色取 TH 原型（上游全黑）。
- 悬挂标签：手持 TC4R `thaumcraft:jar_label` 右键方块侧面放置，需研究 `isorropia:hanginglabel`，罐子（`EssentiaJarView`）上不放。
- 子类实体的战利品表引用原版表（猪/蜘蛛/羊/鸡/兔/牛）；矿猪掉 1–3 石头。
- 未移植：`EntityAILigo`、`EntityAIZapAttack`、`ModelGuardianPanther`（上游均无引用）。

### S4 与上游的偏差

- **治疗缸成型**：用法杖右键未成型的框架（`data` 无需注册表；TC4R 没有“法杖右键 → 多方块”扩展点，所以走 Forge 的 `RightClickBlock` 事件，在法杖自己处理之前接管），需研究 `isorropia:curativevat`，扣水/土/秩序各 50 vis（照 TH 原值）。结构照 TH：上下层大木木板环 + 奥术构造体中心，中间两层玻璃环 + 两格水源；成型后中间两格中心变成 `vat_interior`。
- **配方改为数据驱动**：`data/isorropia/isorropia/creature_infusions/*.json`（19 条）与 `self_infusions/*.json`（10 条），字段见 `common/infusion/*.java`；物品/要素替换沿用 S2 的表，另把 1.12 染料 meta 还原成 1.20 的染料物品（0=黑、1=红、2=绿、3=棕、4=蓝、11=黄、15=白）。
- **要素输入**：缸只吸收“当前需要”的要素（`demand`），从相邻 `EssentiaTransport` 主动抽 1 点；缸本体与四个端口都实现 `EssentiaTransport`，端口转发给主方块实体。
- **vis**：改为 TC4R 的 `VisNetworkApi.drain`（单位 centivis），在缸上方矩阵所在位置按 recipe 的通道抽取；TH 的“抽不到 vis 就损血”没做，改成周期性的不稳定事件。
- **不稳定事件**：保留上游 13 类（喷出基座物品/电弧/负面药水/给玩家加扭曲/爆炸/伤害缸内生物），概率与权重按上游；`!INSTABILITY` 研究在 TC4R 里不存在，故只加扭曲、不改进度。
- **天体灵气**：`CelestialBody` 按 TC4 的日月相位判定；因为 TC4R 没有区块灵气，聚合改成“每周期采 1 点，攒够整份再继续”。
- **灵魂罐**：`isorropia:jar_soul` 物品现在就能用——把生物装罐（消耗 TC 的 `warded_jar`）后对缸右键即放入缸内；罐方块的实体渲染已在 S6 完成。
- **自我注魔**：11 项里 9 项被动能力已实现（`common/infusion/SelfInfusions`）；变形手指的便携工作台界面（S6）与按键开关（S5）后来均已接上并实机验证。上游“失败也把成果写进缸”的 bug 没有照抄，只有成功才记入。
- **肿瘤扭曲**：把玩家扭曲（permanent/sticky/temporary）分档吸进肿瘤、上限 50，身体死亡时原样还回并清空自我注魔（照上游）。
- **收容焦点**：已在 S6 完成（见下文 S6 一节）。
- **研究页**：治疗缸的蓝图页（COMPOUND_CRAFTING）已加（S7 收尾）——TC4R 的 `compound_blueprints` 只对内置的 9 个 handler 真正成型，所以本模组的蓝图只做展示，成型仍由法杖右键事件处理。水格用水桶表示。

### S5 进度明细

已完成（均已在 DEV 客户端实机验证）：

- **透镜系统**：`common/lenses/{Lens,AirLens,FireLens,OrdoLens,EnvyLens,Lenses,LensManager,EnvyStealing}.java`。
  透镜存在**护目镜自身的 NBT**（`isorropia:lenses:{left,right}`）里，与 1.12 一致——护目镜是挂载点，没有护目镜透镜无用。
  火透镜给夜视（装上即生效、卸下即移除，归属用玩家持久数据标记，不会误删玩家自己喝的夜视药水）、
  学者透镜用 TC4R 的 `ScanManager` 每 tick 研究视线目标（包在 `compat/ScanBridge`，1.12 也是每 tick；1.7 的 250 tick 凝视已废弃）、
  嫉妒透镜把生物的掉落「看」出来（`EnvyStealing` 真滚一次掉落表，不带 killer，与 1.12 一致所以吃不到抢夺）、基里安透镜纯客户端绘制。
  网络：`LensChangePacket`（装配/卸下，服务端校验护目镜与物品）。
- **透镜轮盘**：`client/hud/LensWheel.java` + `client/LensKeys.java`（默认 **J**）。按住打开，**准星左右各一个环对应左右两个槽**，
  鼠标在哪半屏就装到哪个槽；潜行 + 按 J 清空两槽。换镜会把旧镜还回背包（满则掉落），不会吞物品。
  环用 `fill` 画（上游用的是 TC 的 radial 贴图，不属于本模组）。**实机已验证**装配与效果，`--gui` 截图确认轮盘像素。
- **躯体大脑**：`ItemSomaticBrain`（Curios 头部）、`ModelSomaticBrain`、`SomaticBrainCurioRenderer`；
  本模组自带 `data/isorropia/curios/{slots/head.json,entities/player.json}` 声明头部槽位（TC4R 只声明了 ring/necklace/belt）。
  1.12 的变换原点与现代 `head.translateAndRotate` 不同，落点按实机第三视角截图定准（`SomaticBrainCurioRenderer` 里一处常量）。
- **能力字段**：`LivingData` 增 `envy`/`hasLooted`（嫉妒透镜 HUD 的前置）并进同步包。嫉妒值记在**被看生物自己**的数据上、`syncTo` 给佩戴者。
- **世界内叠加**：`client/fx/LensWorldFx.java` 在 `RenderLevelStageEvent.AFTER_LEVEL` 里画基里安涟漪/漩涡（活物/亡灵/异界三种配色与旋向）
  与学者透镜的目标名牌；用 TC4R 的 `ThaumcraftClientRenderApi.beginAfterLevelWorldRender` 恢复世界矩阵与深度态。
  世界通道**不依赖 HUD 可见性**，所以它同时负责把目标写进 `LensClientState`（按 F1 隐藏 HUD 时名牌仍在）。
- **屏幕读数**：`client/hud/LensHud.java`（注册在 HOTBAR 之上）——学者透镜在准星处画目标名牌与要素行
  （生物走 `AspectQueryApi.entity` 只画要素、方块走 `placedBlock`、方块上的掉落物走 `item`），
  嫉妒透镜在右下角画 `invidia_hud.png` 进度条（绿色填充自下而上，按 `envy / maxHealth × 5.15` 折算）。
  已知偏差：`AspectQueryApi.block(state)` 对原版放置方块返回空，必须用 `placedBlock(stack, state)`（见 VALIDATION）。

未完成：

- ~~变形手指（`morphicFingers`）的便携奥术工作台界面~~：已在 S6 完成。
- 躯体大脑的「知识拦截」：1.12 只有头部饰品渲染，没有拦截逻辑，本移植同样不做。

### S6 进度明细

已完成（均在 DEV 客户端实机验证）：

- **变形手指**：`common/menu/{MenusIS,MorphicFingersMenu}`、`client/gui/MorphicFingersScreen`、`OpenFingersPacket`、
  `client/MorphicKeys`（默认 **M**）。长了手指（自我注魔 `morphic_fingers`）的玩家按 M 打开**便携奥术工作台**：
  匹配 TC4R 的奥术配方；vis 先从玩家所在的 vis 网络抽（与治疗缸同一套），不够的部分由六个要素水晶槽补（一颗水晶 = 一点 vis），
  界面沿用 TC 工作台的要素环；关闭时格子里的东西全部掉出（上游也不保存）。服务端收包后再查一次手指，客户端说了不算。
- **灵魂信标复生**：`common/infusion/SoulBeacon`。绑定的玩家受致死一击时，先照常结算旧身体（还肿瘤扭曲、清空自我注魔），
  然后若信标仍在、其下方治疗缸里有待命身体（`MODE_SELF_READY`），就取消死亡：原地炸开并洒通量，背包与饰品全掉，
  传送进缸、获得新身体的能力、被缸收着（右键出缸），信标解绑（一次性）。
  顺带修了缸挪动玩家的方式（改走 `connection.teleport`），之前玩家进出缸都只改了服务端坐标、会被客户端拉回。
  维度改按名字存（上游存数字 id，原移植误用了 `hashCode`）。

与上游的偏差：

- 上游复生后会在客户端把物品栏数组清空（修补它自己的不同步）；1.20.1 的 `dropAll()` 会正常同步，不需要。
- 撤掉了一条非上游的捷径：玩家钻进带待命身体的缸再出来就能拿走能力（上游只有复生这一条路）。

- **收容焦点**：`common/items/ItemFocusContainment`（实现 TC4R 的 `FocusIdentityItem`，升级存在物品 NBT）、
  `common/focus/ContainmentFocus`（经 `FocusActionApi` 注册的持续施法动作）、`client/fx/ContainmentFx`（TC4 的持续法杖光束）、
  焦点档案 `data/isorropia/thaumcraft/focus_catalog/focus_containment.json`（**文件名必须等于焦点 id 的 path**，否则 TC4R 拒绝加载整个目录）、
  免疫标签 `data/isorropia/tags/entity_types/containment_immune.json`、注魔配方 `recipes/infusion/focus_containment.json`。
  机制照 Thaumic Horizons（TC4 原型）：按住右键发出 10 格光束，每 tick 付 aer+perditio 各 10 centivis（与法杖上的节俭升级、杖帽折扣联动）；
  光束落在生物上时累积「收容量」，首 tick 为 2+效能/2，之后每 tick 2+效能/3，同时每 tick 衰减 1；
  超过**生物当前血量 ×20** 即装罐（所以先打残更快，与研究文字一致），消耗一个空守卫罐（或空的灵魂罐；创造模式不耗）。
  无空罐时光束立即停止并提示。音效照 TH：抓住生物期间每 1.2 秒 `jacobs`，装罐时 `craftfail`。配方照 TH：传送门焦点 + 琥珀×4、蛛网×2、铁栏杆×2，
  vacuos/vinculum 各 32、iter 8，不稳定度 6。
- 顺带修了灵魂罐的一个隐患：找「可用的罐子」时会把**已装了生物的灵魂罐**也算进去并消耗掉，等于把里面的生物删了；现在只认空罐。

与上游的偏差（收容焦点）：

- 1.12 的收容是 TC6 的「焦点效果」（在焦点操纵台里组合），TC4R 没有这套体系，所以照 TC4 原型做成独立焦点物品。
- 收容量按「玩家 + 目标」记：换目标就从零开始（TH 只按玩家记，换目标会继承进度，属可钻的空子）。
- TH 的 `slow` 升级已补：照 TH 在第 2、4 阶与节俭、效能并列可选（最高 2 级，vinculum 8，图标沿用 TH），光束命中可收容的生物时给 40 tick 缓慢、等级为升级层数−1；其余阶仍只开放节俭与效能。
- **装罐的传送门特效不做**：TH 的 `FXContainment` 在批次外加顶点、随后被自己的 `startDrawingQuads` 清掉，原版里从来没显示过；1.12 版本的特效函数是空的。
  装罐只有声音，与玩家当年实际看到的一致。

- **改造矩阵**（逐项对照 1.12 后修正）：法杖第一下开缸（启动音量 0.5）；之后潜行且有存量通量则排通量，否则先重新清点稳定器再开始注魔；
  找不到可注魔的东西时**什么也不做、缸保持开启**（之前自创了关缸、提示语和失败音效，已删）。拆掉矩阵时：注魔中则缸解体，
  无论如何缸都停下且启动量立即归零（之前只在缸开着时处理、且启动量靠自然回落）。
  稳定度改为上游的四档（极稳/稳定/不稳/极不稳，以 ±25 与 12.5 为界），每周期侵蚀 = 配方不稳定度 ÷ 档位系数（5/6/7/8），之前固定除以 6。
- **矩阵的护目镜读数**：1.12 戴护目镜看矩阵时会显示稳定度档位、存量通量、天穹灵气进度、每周期对称回补与最大损耗；
  TC4R 的护目镜浮层不允许方块追加文字，所以由 `client/hud/MatrixReadout` 自绘在准星下方。对称回补与配方不稳定度随缸同步给客户端。
- **灵魂罐**（逐项对照 1.12 后修正）：补上「手持空守卫罐右键缸 → 把缸里的生物装回灵魂罐」（上游有、之前缺）；
  罐名改由物品按内容生成（`ItemJarSoul#getName`，「Jarred 猪」），不再给物品盖一个固定的自定义名字（之前拆罐掉落时名字还显示成 `minecraft:pig`）；
  法杖打开放置的罐子时生物出现在罐子原位（之前在上方一格）。

与上游的偏差（矩阵与灵魂罐）：

- 矩阵只能放在缸的**主方块**（顶层中心）上方；上游允许放在任意缸方块上，但只有放在主方块上才真正能用。
- 注魔中拆矩阵：本移植的缸解体会掉出已消耗的原料并杀死缸内生物、喷出通量；上游是爆炸并污染灵气，TC4R 没有区块灵气，故不照搬。
- 从缸里装回罐子时不允许在注魔进行中操作（上游没拦，会让注魔失去对象）。
- 物品形态的灵魂罐不画罐内生物：上游的物品渲染也只画罐子本身。

## 范围外新增：TH 独有的内容

上面各阶段只移植 **Isorropia 已有的功能**。TH（GTNH 1.7.10）另有约 7 个整块系统是 Isorropia 从未承接的
（位面、血液注射、灵魂机器、能量节点、自定义傀儡、重构器、材料晶体等），本节记录**从 TH 补进来的、超出原范围的**内容。

### 照明焦点（`isorropia:illumination`）

来源：TH `common/items/ItemFocusIllumination.java`、`common/blocks/BlockLight.java`、`BlockLightSolar.java`、
`common/tiles/TileLight.java`；研究 `focusIllumination`（TH `ThaumicHorizons.java:1721-1739`）。

- **焦点**：`ItemFocusIllumination`（`FocusIdentityItem`）+ `common/focus/IlluminationFocus`（TC4R `FocusActionApi`，激活方式 `USE_BLOCK`，一次性施法）。
  对准方块面右键，在其相邻空气格留下一个「光珠」；消耗 **ignis 100 + aer 100 centivis**（TH 原值），frugal 折扣经 TC4R 的 `vis_discount` 参数生效。
- **光珠方块** `isorropia:light`：小方块（0.3–0.7），无碰撞、无掉落、即破、可被任何方块直接替换；发光 14（普通）/15（solar）。
  16 色调色板与 TH `ItemFocusIllumination.colors` 完全一致。照 TH，方块本身不画任何几何（TH 的渲染类型从未赋值），
  可见的是 `TileLight` 在客户端每 tick 以 `1/(9-粒子档位×2)` 的概率在 (0.55, 0.5, 0.55) 处放出一颗 0.5 大小、无重力、不缩小的 TC4 wisp。
- **换色**：TH 的 `RecipesFocusIlluminationDyes` 照搬为特殊合成 `isorropia:illumination_dye`：恰好一个照明焦点 + 任意染料，
  最后一个染料生效，混入别的物品或第二个焦点则不成立。调色板下标是 1.7 的染料 metadata（黑 0 … 白 15），即 `15 − DyeColor.id`。
  物品名与图标按颜色变（TH 的 16 个 `focus_illumination.N` 图标与「Wand Focus: Illumination (Red)」等名称）。
- **solar 升级**：TH 的 `FocusUpgradeType.solar`（ordo 8 + vacuos 4），第 3 阶可选；装上后光珠变成 solar 光珠，走进去的亡灵着火 5 秒（照 TH）。
- **配方**：TH 是**坩埚**配方（萤石 + lux 12 / potentia 8 / ignis 8），照搬为 `isorropia:crucible/focus_illumination`。
- **研究**：`isorropia:illumination`，父研究 `FOCUSFIRE`，CONCEALED，坐标 (-1, 6)。

与 TH 的偏差：

- **颜色存放位置**：TH 用物品 metadata，本移植放在焦点 property `isorropia:colour`（默认 11＝黄）；装上法杖后焦点头的颜色仍是
  焦点档案里的固定黄色（TC4R 的焦点档案颜色不随物品变），TH 的焦点头会随颜色变。
- **黑色光珠的混合方式**：TH 只对黑色（0）改用普通混合，其余加色；TC4R 公开的 `spawnWispMote` 不能选混合方式，
  黑色 wisp 在加色下几乎看不见。其余 15 色与 TH 一致。
- **方块与太阳光珠合并为一个方块**（TH 是 `BlockLight` + `BlockLightSolar` 两个类），靠 `solar` 属性区分。

### 液化焦点（`isorropia:liquefaction`）

来源：TH `common/items/ItemFocusLiquefaction.java`；研究 `focusLiquefaction`（TH `ThaumicHorizons.java:1673-1695`）。

- **焦点**：`ItemFocusLiquefaction`（`FocusIdentityItem`）+ `common/focus/LiquefactionFocus`（TC4R `FocusActionApi`，
  `USE_AIR`/`USING_TICK`/`STOP_USING`，`CONTINUOUS` 持续光束）。按住右键持续输出一条灼热光束。
- **光束效果**（按 TH 的 `processBlock` 优先级）：
  - **熔融**：石→岩浆（慢）、冰/浮冰/蓝冰/霜冰→水（下界为空气）、草方块/菌丝/土径→泥土、TNT→引爆、雪块/树叶/花/树苗/作物/藤蔓/蛛网→空气。
  - **熔炼**：其余有熔炉配方的方块按熔炉结果产出（方块变方块或掉物品）。
  - **点燃**：可燃烧的方块被点燃。
  - **掉落物**：地上的物品实体被就地熔炼（如粗铁→铁锭）。
  - **生物**：只受少量火焰伤害（`1 + 0.5×potency`），并着火 4 秒，并非武器。
- **破坏进度**：照 TH，光束先在方块上「积累」一个与硬度相关的进度（石头 20、可熔/可燃 0.25、普通熔炼 2.0），
  每 tick 累加 `0.15 + 0.05×potency`，满则生效；换目标即清零。进度按玩家记在服务端。
- **升级**：`frugal`（折扣）、`potency`（加快、加伤）、`enlarge`（第 3 阶，扩到 3×3×3 邻域，逐格收费）、
  `nuggets`（第 1/2/4 阶，按 TH 的 45%/级掉地狱熔炉式额外产物）、`purity`（第 5 阶，把矿石净化为原生晶簇而非普通金属）。
- **消耗**：每熔掉一格 **ignis 40 centivis**（TH 原值），打生物改走命名成本 `critter`（ignis 15）。
- **配方**：TH 是**注魔**配方（不稳定度 3，ignis 12 / metallum 8 / permutatio 4；催化物钻石；材料 火焰焦点 + salis mundus + 挖掘焦点），
  照搬为 `isorropia:infusion/focus_liquefaction`。
- **研究**：`isorropia:liquefaction`，父研究 `isorropia:illumination` + `FOCUSEXCAVATION`，CONCEALED，坐标 (1, 8)。

与 TH 的偏差：

- **`nuggets` 走 TC4R 20732 的地狱熔炉配方表**：TH 调 `ThaumcraftApi.getSmeltingBonus(stack)` 取单条奖励；
  20732 已改成数据驱动的 `thaumcraft:infernal_smelting` 配方（`bonus: true` 的产物）。本移植扫描 `TCRecipes.INFERNAL_SMELTING`
  里匹配该方块的配方、取其中的 bonus 产物，因此**不区分 append/replace 模式与优先级**——奖励表本身是逐条附加的，实际效果一致。
- **`purity` 直接掉原生晶簇，不再代玩家二次熔炼**：TH 会拿晶簇再查一次熔炉配方。TC4R 里晶簇的熔炼是 `thaumcraft:common_metal_smelting`
  自定义配方（晶簇→2 锭），本移植改用 `ArcaneBoreMiningResults.replace(...)`（TH `Utils.findSpecialMiningResult` 的 20732 等价物）
  直接掉晶簇，玩家自行熔炼即可得到双倍金属，也更贴合研究文本「净化矿石为原生晶簇」。
- **光束观感**：TH 用 `beamCont` + `smeltFX` 粒子；本移植依赖 TC4R `CONTINUOUS` 施法自带的预测光束，并复用 `SoundEvents.FIRE_AMBIENT`
  作光束嘶声（每 24 tick 一次）。未逐帧还原 TH 的熔炼粒子。


### 分解焦点（`isorropia:disintegration`）

来源：TH `common/items/ItemFocusDisintegration.java`；研究 `focusDisintegration`（TH `ThaumicHorizons.java:1696-1719`）。

- **焦点**：`ItemFocusDisintegration`（`FocusIdentityItem`）+ `common/focus/DisintegrationFocus`（TC4R `FocusActionApi`，
  `USE_AIR`/`USING_TICK`/`STOP_USING`，`CONTINUOUS` 持续光束）。按住右键持续输出一条紫色光束。
- **光束效果**（照 TH 的 `processBlock` 优先级，实体优先于方块）：
  - **方块**：只有**带要素**的方块才会被分解。光束按硬度积累进度（照挖掘焦点的算法），满了之后把方块「解绑」为结晶要素——
    每个要素按点数掉出对应数量的一颗 `thaumcraft:crystal_essence`，然后方块消失（先过 `GameplayHooks.fireBlockBreak`，尊重保护/事件）。
  - **掉落物**：带要素的物品实体被整叠分解，每个要素掉出 `点数 × 叠数` 颗结晶要素。
  - **生物**：只受少量「分解」伤害（`0.5 + 0.25×potency + 0.5×enervation`，`ThaumcraftDamageTypes.dissolve`）。
- **进度**：与挖掘焦点一致——首次瞄准新方块的那一 tick 只记录目标、不加速度；之后每 tick 累加
  `0.05 + 0.015×potency`，满则生效；换目标即清零。进度按玩家记在服务端，并用负的 `destroyBlockProgress` 覆盖层给施法者显示裂纹。
- **升级**：`frugal`（折扣）、`potency`（加快、加伤）、`enlarge`（第 3 阶，主方块破后连带周围一整壳方块，逐格收费）、
  `enervation`（第 5 阶，本模组新增升级，命中生物附加 2 秒缓慢 I）。
- **消耗**：每分解一格 **ignis 30 + perditio 30 + terra 30 centivis**（TH 原值），打生物改走命名成本 `critter`（ignis/perditio/terra 各 12）。
- **配方**：TH 是**注魔**配方（不稳定度 7，perditio 32 / praecantatio 24 / vitreus 32 / permutatio 16；催化物挖掘焦点；
  材料 液死桶 ×2 + 原始护符 + 炼金煤），照搬为 `isorropia:infusion/focus_disintegration`。
- **研究**：`isorropia:disintegration`，父研究 `isorropia:liquefaction` + `LIQUIDDEATH`，CONCEALED，坐标 (3, 8)。

与 TH 的偏差：

- **父研究已恢复为 TH 原样**：`isorropia:liquefaction` + `isorropia:alchemite` + `LIQUIDDEATH`。早期因为还没有炼金炸药而去掉了中间一项，
  炼金炸药移植后补回。
- **结晶要素物品用 TC4R 的组件式实现**：TH 用 `ItemCrystalEssence` 的 NBT 存单个要素、靠**堆叠数**表示点数；本移植用
  `ThaumcraftItemApi.createCrystalEssence(aspect)` 生成组件式结晶，再把 `ItemStack` 的 `count` 设为点数，语义一致。
- **不可摧毁的产出物**：TH 用 `EntityItemInvulnerable`（重写 `attackEntityFrom` 返回 false）。1.20.1 无该子类，改用普通
  `ItemEntity` + `setInvulnerable(true)`。
- **光束不吞自己的产出**：TH 的 `getPointedEntity` 显式跳过 `itemCrystalEssence`，本移植的 `pointedEssence` 同样排除结晶要素，
  避免光束反复分解刚生成的结晶。
- **光束观感**：同液化焦点，依赖 TC4R `CONTINUOUS` 自带光束，并复用 `TCSounds.ZAP` 作光束噼啪声（TH 原为 100ms 间隔，本移植
  放慢到 5 tick 一次）。未逐帧还原 TH 的 `disintegrateFX` 粒子。


### 活化焦点（`isorropia:animation`）

来源：TH `common/items/ItemFocusAnimation.java`（+ `common/entities/EntityGolemTH.java` 的方块钱魔像部分）；研究
`focusAnimation`（TH `ThaumicHorizons.java:1740-1769`）。

- **焦点**：`ItemFocusAnimation`（`FocusIdentityItem`）+ `common/focus/AnimationFocus`（TC4R `FocusActionApi`，
  `USE_BLOCK` 单次触发，`INSTANT`）。右键点方块，把它「叫」出来变成会走路的魔像。
- **判定**（照 TH 的 `onFocusRightClick`）：非空气、无方块实体（`hasBlockEntity()`）、非流体/植物、硬度 ≥ 0（不挖基岩），
  且要么是实心不透明方块（`canOcclude()`），要么在透明白名单里（蛋糕、仙人掌、玻璃、浮冰、冰、蛛网、TNT）。
- **魔像**：`common/entities/EntityAnimatedBlock`（`PathfinderMob`，独立于 TC4R 核心魔像系统）。同步方块状态 + 材质类型
  （`AnimatedBlockType`）+ 两个升级标志；材质决定血量/护甲/移速/火免/回血间隔/攻击力（照 TH 的 16 项材质表，用 1.20.1 方块标签
  映射）。寿命 = `600 + 200×extend` tick（照 TH）；到寿或被打死时**变回原方块**（就近找可替换位置放下），TNT 魔像或带
  `detonation` 的魔像则**爆炸**。
- **行为**：跟随召唤者（自写 `OwnerFollowGoal`）、攻击怪物；带 `berserk`（第 3 阶）时攻击除召唤者外的一切（含创造模式玩家豁免）。
- **消耗**：照 TH——**六个原质各 `type.visCost` centivis**（泥土/沙 500、木/植物 750……金属 1250），一次性付清；每种材质不同价。
  目录 `base_cost` 取最低价 500×6，作为「能否施放」的门槛与显示值，实际按材质扣费（TC4R 的 `canAffordCast` 门槛用 `base_cost`）。
- **升级**：`frugal`（折扣）、`extend`（延长寿命，走目录参数 `duration`：base 600、每级 +200）、`berserk`（第 3 阶，本模组新增）、
  `detonation`（第 5 阶，本模组新增）。
- **配方**：TH 是**注魔**配方（不稳定度 10，humanus/motus/spiritus/fabrico 各 64；催化物 `itemEldritchObject:3`；材料
  2 个魔像核心 + 2 支人类注射器 + 6 种魔像升级），照搬为 `isorropia:infusion/focus_animation`：催化物 `thaumcraft:eldritch_object`，
  材料 `golem_core_guard` ×2 + `thaumic_charm` ×2 + 6 种 `golem_upgrade_*`。
- **研究**：`isorropia:animation`，父研究 `isorropia:disintegration` + `PRIMPEARL`，CONCEALED，坐标 (5, 8)。

与 TH 的偏差：

- **魔像实现自成一系**：TH 的活化方块钱魔像是 `EntityGolemTH`（TH 自有的完整魔像实体，带工作核心/升级/家域等）。本移植不复用
  TC4R 的核心魔像系统，另写一个轻量 `EntityAnimatedBlock`，只保留 TH 活化方块的玩法（材质属性、跟随、战斗、到寿变回方块）。
- **渲染用方块本体**：TH 造了一套方块贴图的人形模型；本移植直接用 `BlockRenderDispatcher.renderSingleBlock` 把方块本体画在实体
  胸口高度（带轻微上下浮动），观感更朴素但一眼可辨是「被活化的方块」。
- **注射器与核心的替换**：TH 的 `itemSyringeHuman`、`itemGolemCore:100` 在本整合包无对应物，改用 `thaumcraft:golem_core_guard`
  与 `thaumcraft:thaumic_charm` 凑齐 10 个材料，保持配方规模一致。
- **不设家域**：TH 调 `golem.setHomeArea(...,32)`；本移植靠 `OwnerFollowGoal` 跟随召唤者，未做家域束缚。


### 碎片嬗变（`isorropia:shardtrans`）

来源：TH `ThaumicHorizons.java:975-1021`；纯数据（无新方块/物品）。

- **配方**：六条 TC4R 坩埚配方 `isorropia:crucible/shard_trans_{fire,water,air,earth,order,entropy}`——催化物 `thaumcraft:balanced_shard`，
  各需对应原质 2 点 + `permutatio` 1 点，产出六种原质碎片之一（照 TH：EXCHANGE=permutatio、CRYSTAL=vitreus）。
- **研究**：`isorropia:shardtrans`，无父研究、`SECONDARY`；要素 `permutatio` 4 / `vitreus` 4 / 六原质各 2（照 TH），坐标 (11, 6)，
  文本页 + `CRUCIBLE_CRAFTING` 页（`COMPLETED_CRUCIBLE_VARIANTS`，把六条配方都列在一页）。研究文字用 TH 原文。
- **验证**：`96_shard_trans` 一次摆六个沸腾坩埚，各投一颗平衡碎片，六个原质碎片全部产出（`made=6/6`）。


### 炼金炸药（`isorropia:alchemite`）

来源：TH `common/blocks/BlockAlchemite.java`、`common/entities/EntityAlchemitePrimed.java`、
`common/lib/ExplosionAlchemite.java`、`client/renderer/entity/RenderAlchemitePrimed.java`；研究 `alchemite`。

- **方块** `isorropia:alchemite`：照 TNT 的点火路径（打火石/火焰弹右键、红石信号、燃烧的弹射物、被别的爆炸波及）引爆成 `EntityAlchemitePrimed`；
  硬度 0.7 / 抗性 1.0 / 发光 7 / 草质音，贴图直接用 TH 的 `alchemite_{side,top,bottom}`。
- **引信实体** `EntityAlchemitePrimed`：`fuse=80`、有重力、`0.98³` 体积，抛掷初速与摩擦照 TH；引信到点后交给爆炸逻辑。
- **爆炸** `common/explosion/AlchemiteExplosion`：半径 5，生物吃 `dissolve` 伤害；**波及的方块不再掉原物品，而是按要素碎成结晶要素**
  （要素取自 `AspectQueryApi.placedBlock(stack, state)`，掉落物不可摧毁）。灵气节点单独处理——25% 碎成结晶要素并消失、25% 变 `fading`、其余变 `unstable`。
  节点的抗性高达 200，照 TH「先判节点、再算抗性」的顺序，用自定义 `ExplosionDamageCalculator` 对节点返回零抗性，否则射线根本够不到它。
  因为用的是 `BlockInteraction.KEEP`（不然原版会自己掉原物品），爆炸音效与大烟环由自己补发。
- **客户端**：`RenderAlchemitePrimed` 复用原版 `TntMinecartRenderer.renderWhiteSolidBlock` 画方块本体并在引信将尽时闪白；
  爆炸时另发 `ClientFxPacket.BURST`，让客户端撒一圈 TC4 的 `FXBurst` 粒子（TH 的 `alchemiteFX`）。
- **配方**：TH 是**坩埚**配方（TNT + potentia 4 / vitreus 4 / permutatio 4），照搬为 `isorropia:crucible/alchemite`。
- **研究**：`isorropia:alchemite`，隐藏父研究 `ESSENTIACRYSTAL`，坐标 (3, 6)，文本用 TH 原文。
- **验证**：`97_alchemite` 摆一圈石头加八个灵气节点、点一颗短引信炸药；`98_alchemite_check` 读回——石头碎成 terra 结晶、**没有**掉出石头物品、
  炸药块本身被炸掉、八个节点无一保持原样（`plainNodes=0`）。

与 TH 的偏差：

- ~~未做发射器行为~~：已补（`AlchemiteDispenseBehavior`，照 TH：从发射器正前方一格放出已点燃的炸药并播 TNT 点燃声）。
- **对生物的伤害偏低**：TH 自己算伤害（`(x²+x)/2 × 16 × 2r + 1`，最高约 161），本移植用原版爆炸的伤害公式（系数 7，最高约 71），伤害类型仍是 TH 的 `dissolve`、击退相同。原版 `Explosion` 不允许换系数，要还原需要重写整个爆炸，暂不做。
- **结晶要素按堆叠上限拆开**：TH 把一个要素的全部点数塞进一堆（节点可上百点），本移植拆成多堆，避免超过 64 的非法堆叠。
- **爆炸粒子**：TH 用 TC 的 `FXBurst`；本移植复用 TC4R 的 `spawnBurst`（同一效果）。


## S4：能量与节点系统（TH 独有，4 台已完成本批验收）

TH 的 5 个能量方块（visDynamo / essentiaDynamo / nodeMonitor / syntheticNode / transductionAmplifier）串成一条研究链。
本移植按此顺序逐个加入：Vis Dynamo、精华发电机、节点监视器、合成节点（以太碎片）已完成本批验收，已知偏差见各节。2026-10-12 完整客户端回归 82 步通过；转导放大器待位面线与兼容接口评估，并非已判定无法移植。

### Vis Dynamo（`isorropia:vis_dynamo`）

来源：TH `common/tiles/TileVisDynamo.java`、`common/blocks/BlockVisDynamo.java`、`client/renderer/tile/TileVisDynamoRender.java`；
研究 `visDynamo`（TH `ThaumicHorizons.java:1067-1087`）。

- **方块 + 方块实体**：`BlockVisDynamo`（`EntityBlock` + TC4R `WandInteractionTarget`）+ `TileVisDynamo`（实现 TC4R `VisSource`）。
  硬度 0.7 / 抗性 1.0 / 发光 8（TH 原值），金属音。
- **充能**（照 TH 的 `onWandRightClick` + `onUsingWandTick`）：手持法杖右键 → 进入「使用中」状态；此后每 5 tick 一次脉冲，
  每次脉冲对**每个启用的原质**从法杖抽 **40 centivis**，成功则给该原质留 **5 centivis**，并把供给窗口重置为 **10 tick**。
  供给期内每 tick 把这份「报价」铺成网络可取的额度，因此相当于以每 5 tick 40 cV 的代价，向网络提供 5 cV/tick。
  法杖付不起的原质当次即静默（TH 每脉冲整体重建 `primalsProvided`，此处一致）。
- **网络接入**：只有当 `ticksProvided > 0` **且**六面中有一面是 TC4R 的 `thaumcraft:vis_relay` 时才作为源注册
  （`VisNetworkApi.registerProvider/registerSource/removeSource`）；`networkEndpoint()` 返回那个继电器。玩家松手、走开、
  转开视线或法杖耗尽 → 供给窗口自然衰减到 0 → 注销。TH 是「必须贴着 vis net」，此处等价。
- **模拟/执行**：`takeVis` 在 `SIMULATE` 下不扣任何额度，只有 `EXECUTE` 才扣，符合 TC4R 的两段式网络协议。
- **配方**：TH 是**注魔**配方（不稳定度 5，auram 4 / potentia 16 / permutatio 16 / machina 16；催化物活塞；
  材料 原质护符 + 金锭 + 大木板 + 金锭），照搬为 `isorropia:infusion/vis_dynamo`（原质护符用 TC4R 的 `thaumcraft:thaumic_charm`）。
- **研究**：`isorropia:visdynamo`，隐藏父研究 `INFUSION` + `VISPOWER`，坐标 (7, 8)。

与 TH 的偏差：

- ~~未做外观渲染~~：已补（见下方「外观」一节）。
- **要素选择界面已补**：照 TH，手持法杖以外的东西右键打开 `VisDynamoScreen`（`guidynamo.png`，111×104），六个原质图标的
  位置、命中区（第二行点击区比绘制高 2 像素、边界含右/下沿）、关时 TC4 近黑色（0.1 灰、0.8 透明）与点击闪光（粒子表第 8 行，8 帧）照 TH；
  点击走原版菜单按钮包（TH 用的是同一个附魔按钮包），服务端 `clickMenuButton` 切换对应原质，数据槽把开关同步回界面。
- **按住充能靠方块实体自己的 ticker**：TC4R 的法杖 `onUseTick` 只派发焦点会话，没有 TC4 的 `IWandable.onUsingWandTick`。
  所以「按住」由 `TileVisDynamo` 每 tick 回读「该玩家仍在用这把法杖、且视线还落在这个方块上」来判断（照 TC4R 自己的
  `WandNodeTapping` 写法）。TC4 里按住的 `IWandable` 会接管法杖，焦点不再施放；TC4R 只为自己的节点这样做，
  其余方块照常每 tick 跑焦点（内置火焰/电击/挖掘，以及附属焦点的 `USING_TICK`）。本移植用 `mixin/WandItemMixin` 在
  `WandItem.onUseTick` 开头询问 `common/compat/HeldWandTargets`：由本模组机器方块开启的按住，焦点不运行；视线离开机器时照 TH 停止使用法杖。
  这是对 TC4R 内部类的注入，见 CLAUDE.md 的版本升级复核点；注入缺失时启动日志会警告。
- **挖掘掉落**：原先设了「需要正确工具」却没进任何可挖掘标签，用什么挖都不掉落；已加进 `mineable/pickaxe`。

### 外观（三台机器共用）

照 TH 的 TESR 重做：`client/renderer/tiles/RenderDynamo`（两台发电机共用，构造时给环和底座贴图）与 `RenderNodeMonitor`。
TH 的模型贴图放在 `textures/entity/machines/`（TC4R 会把所有命名空间的 `textures/models/` 拼进方块图集，所以不能放那里），
`scanner.obj` 直接用 TC4R 自带的，`hexagon.obj` 从 TH 拷来。环的升降/转动在客户端由 `DynamoMotion` 按 TH 的每 tick 步长推进，
服务端只在「开始/停止供给」或「供给的原质组合变化」时同步一次。物品由 `MachineBlockItem` + `MachineItemRenderer`（BEWLR）
交给同一个方块实体渲染器画。

### Essentia Dynamo（`isorropia:essentia_dynamo`）

来源：TH `common/tiles/TileEssentiaDynamo.java`、`client/renderer/tile/TileEssentiaDynamoRender.java`；研究 `essentiaDynamo`。

- **机制**（照 TH 的 `updateEntity`/`drawEssentia`/`addEssentia`）：只从**正下方**的精华管道/罐取精华；每单位提供 **21 tick** 的供给，
  每 tick 共 **12 cV**，平分到这种精华拆出来的原质上（火→12 火；生命→地/水各 6；人类→六原质各 2），研究原文称「每秒一单位」。
  剩余 ≤20 tick 时对下方吸力 128；供给用完那一 tick 自己再抽一单位，抽不到就停机。和魔力发电机一样经旁边的 vis 中继接入网络。
- **配方**：TH 注魔（不稳定度 5；活塞为中心；神秘金属锭 ×2、炼金构造体、大木板、炼金离心机；auram 4、potentia 16、permutatio 16、machina 16、aqua 32）。
- **研究**：`isorropia:essentiadynamo`，父研究 `isorropia:visdynamo`，隐藏父研究 `INFUSION`/`VISPOWER`/`THAUMIUM`，SECONDARY + CONCEALED，坐标 (9, 8)。

与 TH 的偏差：

- 精华流光：TH 在 TESR 里每帧生成一条，本移植每 tick 生成一条，避免帧率高时变密。

### Node Monitor（`isorropia:node_monitor`）

来源：TH `common/tiles/TileNodeMonitor.java`、`common/blocks/BlockNodeMonitor.java`、`client/renderer/tile/TileNodeMonitorRender.java`；研究 `nodeMonitor`。

- **机制**：只能贴着灵气节点放置（点哪一面就朝哪一面；点的不是节点就找任一相邻节点），朝向背离节点。节点的任一要素（包括已经抽干到 0 的）
  **≤ 1** 时输出 **15 级强信号**，否则 0；节点没了自己掉落成物品（TH 的 `killMe`）。节点状态经 TC4R 公开的 `AuraNodeView` 读取。
- **配方**：TH 注魔（不稳定度 2；神秘仪为中心；僵尸脑、红石比较器、风之碎片；auram 2、sensus 8、machina 8）。
- **研究**：`isorropia:nodemonitor`，隐藏父研究 `INFUSION`，坐标 (7, 6)。

### 合成节点 / 以太碎片（`isorropia:ethereal_shard`）

来源：TH `common/tiles/TileSyntheticNode.java`、`common/blocks/BlockSyntheticNode.java`、
`client/renderer/tile/TileEtherealShardRender.java`；研究 `etherealShard`（TH 里方块注册名是 `synthNode`，
但显示名、研究键与渲染器都叫 ethereal shard，本移植取后者）。

- **方块 + 方块实体**：`BlockEtherealShard`（`EntityBlock` + TC4R `WandInteractionTarget`）+ `TileEtherealShard`。
  硬度 0.7 / 抗性 1.0 / 发光 7（TH 原值）、玻璃音、碰撞盒 0.3–0.7 全高、无遮挡；整体由 TESR 绘制。
- **扩容**（照 TH 的 `addEssence`）：手持以太精华右键，该精华所属要素的**容量**加 `4 × 叠数`，整叠消耗。
  一个碎片本身不含任何容量，必须先喂精华。
- **从网络充能**（照 TH 的 `updateEntity`）：对每个「还有余量」的要素，每 tick 向灵气网络要 **10 centivis**
  （`VisNetworkApi.drain`，消费者位置就是这个方块），沿用 TH 的严格 `> 100` 小数累积判定折成整 vis（恰好 100 暂留，下一次输入再折算）。
  原质直接走自己的通道；**复合要素**要把它的全部原质逐级展开、逐个各要 10 cV，取其中**最小值**入账
  —— 对应 TH 的 `reduceToPrimals` + 取最小，也就是研究原文说的「提供的 centivis 必须含该复合要素的全部原质」。
- **法杖抽取**（照 TH 的 `onUsingWandTick`，并对齐 TC4R 核心节点的 `tapIntoWand`）：手持法杖右键并按住，
  每 **5 tick** 从「法杖还有余量」的要素里**随机挑一个**抽走：基数 1 点，`NODETAPPER1`/`NODETAPPER2` 各 +1，
  上限不超过存量；非潜行且完成 `NODEPRESERVE`、杖杆不是木、杖端不是铁时保留最后 1 点。抽走的整 vis 按
  **1 点 = 100 centivis** 灌进法杖，按实际到账量回扣。
- **被读取为节点**：实现 TC4R 的 `AuraNodeView`，所以**魔导透镜/神秘测魔仪**能像读真节点一样读它
  （TH 的碎片本来就是个 `INode`）。身份用「方块坐标」回退（`instanceId` 为空），类型 NORMAL、无修饰。
  顺带地，节点监视器也能装在它旁边。
- **挖掘掉落**（照 TH 的 `breakBlock`）：每个要素按 **容量 ÷ 4** 掉出等量以太精华（每颗带该要素 2 点），
  数量等于喂入的精华；返还物的要素量固定为 TH 的 2，超出物品堆叠上限时拆成多堆。
- **配方**：TH 是**注魔**配方（不稳定度 7；催化物平衡碎片；材料 4 × 闪耀之光；auram 8 / potentia 32 / vacuos 32），
  照搬为 `isorropia:infusion/ethereal_shard`。
- **研究**：`isorropia:etherealshard`，父研究 `isorropia:shardtrans`，隐藏父研究 `INFUSION`/`VISPOWER`，
  CONCEALED，坐标 (11, 8)，要素照 TH（auram 8 / potentia 16 / vacuos 16 / praecantatio 8），复杂度 3，
  文本用 TH 原文（两页文本夹配方页）。
- **外观**：照 TH 的 `TileEtherealShardRender`——一枚按**容量加权平均要素色**上色的晶体（TH 用 TC4 的
  `ModelCrystal`：box (-16,-16,0;16,16,16)、旋转点 (0,32,0)、X/Z 各 0.7071 弧度、64×32 UV；本移植用独立 ModelPart 层重建，
  不引用 TC4R 内部模型类）。位置种子决定三轴尺寸，按 TH 的 y=-0.15、sin×0.1 浮动与 1°/tick 自转，颜色按容量混合后除以 220；
  每个绑定要素一个绕行符标（nodes 图集第 1 行，32 帧、alpha 0.9，使用 TC4R 公开 UV 映射处理 1088² 图集边距）。
  抽取光带使用分段正弦波、双平面、加色混合、端部淡出与滚动 wispy 贴图，颜色每 tick 按 (目标+4×当前)/5 过渡。

与 TH 的偏差：

- **不向网络供电**：TH 的 `TileSyntheticNode.isSource()` 返回 false，它只**消费**网络，本移植照此，因此不实现
  `VisSource`。
- **按住抽取由方块实体自己判**：TC4R 的节点抽取（`WandNodeTapping`）写死了核心的 `AuraNodeBlockEntity`，
  外挂方块实体接不进去。所以「按住」和魔力发电机一样，由 `TileEtherealShard` 每 tick 回读「该玩家仍在使用这把法杖、
  且视线还落在这个方块上」来判断。右键仅建立抽取状态，转移从后续 tick 开始，空仓也可一直等待充入。
  与魔力发电机共用 `HeldWandTargets`：按住时法杖上的焦点不运行（见魔力发电机一节）。
- **现代光照与手部起点**：几何参数已按 TC4R 对 ModelCrystal 的转写重建；呼吸亮度仍通过现代 lightmap 采样，
  不保证与旧 OpenGL 浮点采样逐像素相同。光带手部起点复用 TC4R 的 `resolveFocusCastOrigin`，不复制旧版视角偏移。
- **移除返还精华**：对应 TH 的 `breakBlock`，改用 `onRemove` 在方块种类改变时结算；普通破坏、创造拆除、爆炸移除均走此路径，
  同方块的状态更新不返还。方块物品本身仍由 loot table 决定。
- **抽取状态会同步**：为了让光带颜色跟着**抽走的要素**走，每次成功抽取都发一次方块实体更新（最多 4 次/秒，
  只在有人正抽时发生）；网络存量增长成整点也发更新，确保符标大小和节点读数不会滞后。TH 同样在增加整点存量时更新方块。

## 位面线（TH 独有，机器部分已完成）

来源：TH `common/items/ItemPlanarConduit.java`、`Tile/BlockTransductionAmplifier`、`Tile/BlockVortexStabilizer`、
`Tile/BlockRecombinator`、`Tile/BlockVortex`，以及 `common/lib/PocketPlaneData` 等。里程碑：

| 内容 | 提交 | 探针 |
| --- | --- | --- |
| 位面导管 + 位面理论（`isorropia:planar_conduit` / `planarTheory`） | `c659872` | `200` |
| 转导放大器（`isorropia:transduction_amplifier`） | 钩子 `02a895f`、机器 `d0a3826` | `202` |
| 漩涡衰减器（`isorropia:vortex_stabilizer`） | `c9e82e3` | `203` |
| 重组器（`isorropia:recombinator`） | `daec906` | `204` |
| 位面漩涡（`isorropia:vortex`） | `541cd4d` | `208` / `209` |
| 衰减器压制漩涡（TH 的 `beams`） | `831d82a` | `208` |
| 口袋位面维度骨架 | `33f4b23` | `210` |
| 成对传送门（位面网关） | `8e9c8ec` | `226`/`227`/`228`/`229` |
| `planarRift` 研究 + 六光束交接 | `ef240cc` | `230`/`231`/`232` |
| 虚空合成（油灰 / 水晶法杖 / 灵气精灵）+ 法杖取物 | 见提交 | `233` |

- **转导放大器**：TH 的 `recipeTransducer` 用 `blockStoneDevice.11`（节点转导器），不是 `.9`（节点稳定器）——首次提交按名字
  错用，已修正（`216e6fe`）。充能节点的基础 vis 由结构签名**每 tick 重算**，公开 `NodeApi` 没有 setter，所以加了 Mixin
  `AuraNodeBlockEntityMixin`（`isorropia.mixins.json`，`required:false`）把「每节点加成表」折进 `calculateEnergizedBase`
  的 RETURN，桥在 `common/compat/AuraNodeBonus`（反射，Mixin 关掉即 no-op）。有效充能节点需要**正下方稳定器 + 正上方转导器**
  （`AuraNodeBlock.hasValidEnergizedStructure`），放大器贴在**侧面**不破坏该结构。
- **四重叠加灾难**：≥4 台放大器时节点被换成漩涡并继承其基础要素。加成必须**先读再撤**——撤销会把派生缓存清空，所以真实底值
  要用 `energizedBase() - AuraNodeBonus.get(node)` 计算。
- **重组器**：TH 的 `processCombos` 有四趟回退，但 `AspectApi.combination` 是「双源要素」表，只有第一趟可能命中，其余三趟等价，
  故只实现一趟并注明。双节点改写走 `NodeApi.replaceLoadedStates`（原子事务、需服务端线程）；`NodeVis` 保留 0 而不允许负值。
  TH 的注魔配方催化物是**始源珍珠**（`itemEldritchObject` meta 3 = `thaumcraft:primordial_pearl`），材料四个
  `thaumcraft:essentia_filter`（= `itemResource.8`，TC4 的「Vis Filter」）。
- **位面漩涡**：不可破坏、亮度 15、无碰撞、不掉落。预热 50 tick 后吞掉自身 5×5×5，然后把 15 格内实体拽入、近身撕咬，并每 tick
  磨掉最多 3 个硬度 0–10 的方块；`count += max(0, 6-beams)`；超过 2400 塌缩，25 tick 后自毁并爆散咒波。衰减器光束同时接受
  节点与漩涡（两者都无碰撞箱，步进扫描先查方块实体再判碰撞），漩涡被照中时 `beams ±1`。
- **TESR 状态必须同步**：原版 `BlockEntity#getUpdateTag()` 返回**空** NBT、`getUpdatePacket()` 返回 null，所以放大器与衰减器
  上线后逻辑正确但**渲染器永远看不到激活**（`2cda6b2` 修正）。探针 `206` 回读**客户端** `mc.level` 的方块实体，是唯一能证明
  同步的方式。
- **TH 放大器档位**：活塞行程与叠加层辉光用 `min(50,count)/137.0`（不是 `/50`），且辉光走**脉动 lightmap** 而非满亮度；
  写错会得到「活塞飞出方块 + 白色糊块」。参考 TC4R 自己的 `NodeMachineRenderHelper.renderTransducer`。

与 TH 的偏差：

- **漩涡的六光束交接已做（阶段 4）**：TH 在 `beams ≥ 6` 时由漩涡开启口袋位面。本移植补上 `planarRift` 研究条目，
  并在 `TileVortex.serverTick` 里照 TH 的 `handlePocketPlaneStuff` → `createDimension`：一颗原始珍珠丢进被压满的
  裂口即盖章出一个位面（`PocketPlaneBuilder.create`），裂口随之成为该位面的门（`createdDimension`）。门把站在其中的
  玩家送进位面，位面出口裂口（`cheat` 门，出生即 `createdDimension`）再把人送回来；穿越免疫照 TH 的
  `timeUntilPortal`，进门 200 tick、被门扣住时重置为 10（`transferTravellers`）。一颗珍珠只开一个位面。
- **虚空合成已做（除傀儡粉）**：TH 的 `handleVoidCrafting` 照搬——丢进被压满的裂口的**虚空锭**变成**虚空油灰**（每锭一份，
  存入裂口内部的 `items` 列表），**盐之精华**迸成 1–4 只（要素取自裂口）**灵气精灵**，**惰性水晶法杖**换出一根
  充能水晶法杖（水晶杖杆 250 vis + 水晶杖端，用 `WandApi.createFullyCharged`）。**法杖右键取物已做**：`BlockVortex`
  实现 `WandInteractionTarget`，`use()` 里把裂口最旧的一件存物弹回。虚空油灰可**修复任意受损的可修复物品**（TH 的
  `RecipeVoidPuttyRepair`，做成 TC4R 的 `CustomRecipe` + `SimpleCraftingRecipeSerializer`）。
  研究条目 `isorropia:wispSpawn` / `voidPutty` / `crystalWand`（父级 `planarRift`，CONCEALED），配方页给水晶法杖。
- **傀儡粉已做（2026-10-14，`8c95531`）**：TH 同一入口里的傀儡粉分支照搬——丢进被压满的裂口的**傀儡动画粉末**
  每捏生成一只**永久虚空傀儡**（`PERMANENT=-420`，无核心，主人=投掷者，TH 的 `spawnWisps` 兄弟分支）。
  TH 的「水晶法杖不可充能」在 TC4R 没有对应标志，本移植由裂口一次性充满（该法杖也只能这样获得）。
- **工作傀儡系统已做（2026-10-14，`8c95531`）**：TH 的 `EntityGolemTH`（`extends EntityGolemBase`，TC4R 的
  `ThaumcraftGolemEntity` 是 final 且材质表是封闭枚举，不能挂靠）做成**独立 `PathfinderMob`**：
  材质表沿用 TH 的 `EnumGolemTHType`（本移植里是 `AnimatedBlockType` 的 `upgrades`/`carry` 扩展列）；
  渲染沿用本移植已验证的「方块身体」画法（`RenderAnimatedBlock` 同款），虚空傀儡以黑曜石材质顶替 TH 的
  `golem_void` 人形皮肤；**TC4R 的核心/升级道具右键安装**（核心=定居守卫，升级每种最多 2 个，同步层没有
  byte[] 序列化器所以按 4bit 一槽打包进 int）。粉末右键方块=TH 的 `onItemUse`（1200 tick 寿命，复用动画焦点
  的可激发方块判定），死亡时**还原回原方块**（TNT/爆裂则爆炸，还原不下就掉落）。研究 `golemPowder`
  （坩埚：盐之精华 + humanus/spiritus/motus 各 4，父级 `GOLEMTHAUMIUM`/`ADVANCEDGOLEM`）与 `voidGolem`
  （隐藏父级 `golemPowder`、父级 `wispSpawn`、warp 1），页面文本照 TH 原文；虚空傀儡的三条警告
  `thaumichorizons.golemWarning1..3` 原样搬进 lang，最后一次「笑声」后死亡并召唤 `TCEntities.ELDRITCH_GUARDIAN`。
  TH 的**工作行为**（搬运/挤奶/伐木等 core 任务）留在 TC4R 自己的傀儡系统里，本实体只做伴随/守卫。
  TH 的傀儡铃（拾取/停用）未移植（TC4R 的铃绑它自己的傀儡类）。
  实机验证：探针 `238`（粉末→ROCK 傀儡 30 血、跟随主人、装 alchemy 核心、2 个 AIR 升级、第三个被拒、
  死后还原石头）+ 探针 `247`（裂口吞 3 捏粉末→3 只无核心 VOID 傀儡、归属投掷者、裂口内无存物）。
- **漩涡的饥饿粒子已做**：`clientTick` 在裂口未被压满时调 TC4R 自己的 `HungryNodeClientEffect.tick`（同 TH 的
  `hungryNodeFX` 拉取方块碎屑）。**循环音效已做**：注册 `isorropia:vortex`，按 `vortex.ogg` 的 27 秒长度每 540 tick
  重放一次（TH 用的是 `MovingSound` 循环）。
- **FXSonic 未做**：TH 的 `FXSonic` 是画 `hexagon.obj` + 15 帧 `ripple*.png` 的自定义粒子，只在衰减器命中处冒三圈；
  本移植的衰减器已有 `RenderVortexStabilizer` 的光束带，故暂不重写一个模型粒子。
- **重组器不阻止普通节点欺凌**（照 TH）；供体掉容量的 1/3 概率保留。

### 并行一轮：三个独立模块（2026-10-14）

用户要求用 workflow + subagent 并行做多个互不依赖的模块，最后由主循环亲自验收。做法：三个 agent 各自在
**独立 git worktree** 里实现（worktree 里没有 git 忽略的 `local-repo`/`reference`，每个 agent 第一步补拷），
**共享文件零重叠**（注射器动 `ItemsIS`/`EntitiesIS`/`lang`/`research`；地形动 `PocketPlaneTerrain`；群系只加新文件；
`PocketPlaneBuilder.create` 由主循环保留），因此合并**零冲突**。三个模块各自提交后合并，再补接线，最后在 DEV 客户端
一次性跑完四个探针并截图。

- **A 注射器线**（`bafd7e0`）：4 种注射器 + `EntitySyringe`/`EntityBlastPhial` + 两条奥术配方 +
  `essentiaInjection`/`blastPhial` 研究。TH 把注射与爆裂药瓶放在一个物品里用 damage 区分，本移植分成两个 id；
  TH 的爆裂药瓶配方产出 `itemSyringeHuman` damage 1，而真正被扔出去的是 `ItemSyringeInjection` 的药瓶形态，
  所以配方改为产出可用形态；`stacksTo(1)` 与「一次做 4 个」在现代 codec 下互斥（1.7 允许结果超过堆叠上限），
  故药瓶可堆叠、注射保持 1。血液注入器与 Seroconverter 是后续模块，故研究缺那一页配方。（Seroconverter 后已补上，见下。）
- **B 洞穴与口袋**（`3d20b51`）：`drawCaves`/`drawPockets` 系列；`drawRavines`/`drawClouds`/
  `drawSurfaceFeatures`/`drawUndergroundFeatures`/`drawLeviathanBones` 在 TH 里本来就是空函数。
- **C 群系与效果**（`adadb0f`）：1.7 的生物群系是逐列的、可按要素动态决定，现代必须静态注册，故定义三个静态位面群系
  + 按要素选择，再用 `ChunkAccess` 的 4×4×4 单元格写入已盖章的区块。TH 的 `addEffects` 只填 `data.potionEffects`
  而全代码无人读取（与「扔不出去的爆裂药瓶」同类的 TH 死端点），本移植把清单存进位面记录并持久化。

### 口袋位面阶段划分

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| 1 | 维度注册（`isorropia:pocket_plane`，虚空 `minecraft:flat`）+ `PocketPlane` 传送助手 + `PocketPlaneStore` 注册表 | 已完成 `33f4b23` |
| 2a | 框架盖章：球壳、要素环、着陆台、出口裂口（`PocketPlaneBuilder`，探针 `211` / `212`） | 已完成 `baeed1a` |
| 2b | 分层地形 `drawLayers` / `drawLayer`（含地表装饰、作物、树木、生物、虚空/奥术石/注魔台点缀），加 TH 的 `dust` 默认层方块 | 已完成（探针 `214`） |
| 2b' | 洞穴与口袋 `drawCaves` / `drawACave` / `drawPockets` / `drawAPocket`，加入口 `drawCavesAndPockets` | 已完成 `3d20b51`（探针 `221`） |
| 2c | 生物群系与效果：TH 的 `setBiome`（三个静态位面群系 + 按要素选）与 `addEffects` | 已完成 `adadb0f`（探针 `222`） |
| 2d | 整合：`PocketPlaneBuilder.create` 按 TH 顺序跑完整链路，维度生成器改用 `isorropia:pocket_plane` | 已完成 `7ce9fab`（探针 `223`/`224`） |
| 3 | 传送门：`BlockGatewayPortal`、`GatewayTeleporter`，以及 `PocketPlaneData.makePortal` / `destroyPortal` 的成对传送门 | 已完成（探针 `226`/`227`/`228`/`229`） |
| 4 | 研究 `planarRift`（TH 的 SPECIAL 研究，父级 `vortexStabilizer` + `transductionAmplifier`，warp 3）与漩涡六光束交接 | 已完成（探针 `230`） |

- 布局照 TH：每个位面占 +Z 方向一列 256 格，居中 `(0,128,256·id)`，半径上限 127，出口裂口在锚点正上方。
- TH 的位面世界本来就是**虚空**，岛屿由 `PocketPlaneData.generatePocketPlane` 直接盖章生成（不是逐区块地形），
  所以维度用虚空生成器即可；阶段 2 的工作是「盖章」而不是写 `ChunkGenerator`。
- 阶段 1 暂用 `minecraft:plains` 生物群系与 `minecraft:overworld` 维度特效；TH 的 `BiomeGenPocket`（按要素上色的生物群系）
  留到阶段 2 一起做。
- 阶段 2a 的探针注意：**截图/探针在口袋位面里运行时，harness 的 `PLACE` 步骤会抛 NPE**——它固定用主世界的
  `ChunkSource` 去 `move(player)`，而玩家此时在另一个维度。截图本身仍会落地（报错发生在抓帧之前）。
- **分层地形的两条关键发现**（阶段 2b）：其一，TH 在层与层之间把 `level` 逐层下调，但 `drawLayer` 里这个值在柱体的顶和底
  都被**抵消**（加进球面方程的偏移又原样减了回来），所以层**不会**叠成带状：每个命中的层都会重画整个球，最终看到的是顺序
  里**最后一个**命中要素的材质。本移植保留该行为。其二，每次 `drawLayer` 都新建一个 `NoiseGeneratorOctaves`（用
  `world.rand`），所以 EARTH 层的泥土与「level−5 的石头」用的是**两份不同的噪声**，结果是两者互相穿插而不是上下分层。
- **噪声实现**：TC4 的 `NoiseGeneratorOctaves` 无法复现（置换表不同），改用自带的 10 阶 Perlin（振幅折半、相同的尺度参数），
  地形轮廓会与 TC4 不同，但阶数与衰减一致。
- **未移植的点缀**：TH 的 `blockBrain`（肉块脑）与 `EntityMercurialSlime` / `EntityMeatSlime` / `EntityTaintacle` /
  `EntityTaintSporeSwarmer` 等本模组没有的实体；凡 `red_flower` 超出身位（TC4 只有 9 种）的掷骰折回这 9 种。
- 出口裂口是 TH 的 `cheat` 裂口：`PocketPlaneBuilder` 会给它补上本平面的要素，否则照 TH 原样（不设要素）它
  因为渲染器要求要素非空而**完全不可见**，玩家会找不到回家的路。
- **成对传送门（阶段 3）的几处要点**：
  - TH 的 `checkBlocksX` / `destroyPortal` **都跳过插槽自身那一格**（5×5 环的 `(0,0)`）。本移植起初把它当成环格，
    于是 `checkRing` 永远返回 false、`destroyPortal` 还会把插槽本身覆盖成奥术石。用 `isRingCell` 排掉该格。
  - `consumeVisFromWand(..., SIMULATE, ...)` 成功时返回的是 `AVAILABLE`（`consumed()==false`），不是 `CONSUMED`；
    开门前的可支付性检查必须用 `affordable()`。TH 的 `consumeAllVisCrafting(..., false)` 就是这个语义。
  - TH 的 `TilePortalTH` 从未被任何方块创建（`BlockPortalTH` 不覆写 `createNewTileEntity`），所以它那套漩涡渲染器、
    环境音和 `timeUntilPortal` 全是死代码。本移植保留 tile（目的地必须存下来），并把它那套渲染器真正用起来。
  - 传送落点正好在对面开口的portal方块里，所以每次穿越都要给 `setPortalCooldown(50)`（TH 的 `timeUntilPortal`）。
    免疫计时器必须**自然衰减**：在 `entityInside` 里每 tick 重刷会让玩家永久卡在传送门面上。
  - 九格表面各画一块四边形：TH 的相机朝向 billboard 会让九块浮离墙面、且视线以下的几块着色异常；改成沿墙面的平面四边形，
    朝向由「哪条水平轴上有相邻 portal 方块」判定（开口只有一格厚，所以必是单轴）。
  - 位面侧的开口刻在位面边缘，而岛屿一直实到边缘，所以 TH 只刻了门面和背后虚空、把开口埋住了；本移植额外向内侧掏两格口袋。


### 研究文字

照项目约定，TH 内容的研究文字一律用 TH 原文：照明/分解/活化焦点与魔力发电机原先是改写或自编的，已换回 TH 原文；活化焦点补上了 TH 的第二页。


### Seroconverter（TH bloodInfuser，2026-10-14）

TH 的 Seroconverter = `BlockBloodInfuser`/`TileBloodInfuser`（"血夜转化器"）：正面放血注射器，向导在表盘上拨最多
八单位要素，机器以 suction 128 从除顶面外的任一面逐 tick 各吸 1 单位，凑齐后吃掉注射器、把当前产物装瓶成注射剂
放进 3×3 输出架。效果照 TH 的 `getCurrentEffects`：每要素把 `amount × 权重` 记到各药水上，每两单位要素出一个最强
剩余药水，倍率=权重/30、时长=权重×100tick（瞬效 heal/harm 为 1），颜色按要素色加权混合。TH 键在 TC6 要素上的
权重表按 TC4R 要素重键（cloth→tutamen、tree→herba），TH 独有药水（vis 回复/真空/电击/合成/vis 耗竭/污浊毒）无
1.20.1 对应，整行弃用。

- **接线**：模式 0/1/2 = 停止/单次/自动，走 `clickMenuButton`（vanilla 的 `sendEnchantPacket`）；负数 id = 把玩家
  已知排序表的第 `(-id)-1` 个要素 +1，>2 = 删第 `id-3` 个，0–2 = 模式。TH 的排序键是玩家自己的已发现要素表，
  两端各自对 `PlayerKnowledgeApi.view` 的清单按要素 id 排序保证一致。表盘与吸入量全部走 BE 的 `getUpdateTag`
  同步（TH 的 GUI 直接读 tile，本移植的屏幕同样通过菜单的 BlockPos 读客户端 tile）。
- **槽位**：0 号只收血注射器（`SimpleContainer.canPlaceItem` + 菜单 shift 点回 0 号），输出 3×3 是 TH 的列主序
  `x*3+y+1`。`Block.onRemove` 落包 10 格。
- **渲染**：TH 的十盒模型（64×64）原 pose 保留，进渲染器时走 TH 的 `translate(0.5,1.5,0.5)+rot180X`；`hasBlood`
  时按 TH 的 `translate(1.90625,0.75,0.46875)+rotZ90` 叠画移植版 `ModelSyringe`。
- **资产**：`guibloodinfuser.png` → `textures/gui/`，`bloodinfuser.png` → `textures/entity/blood_infuser.png`；
  注入研究页 `INFUSION_CRAFTING` 插进 `essentiaInjection` 的 TEXT.2 之后（配方：坩埚居中，比较器×1 +
  炼金构造×2 + 要素缓冲×1，humanus/permutatio 8、victus/praecantatio 16，不稳 4）。
- **运行时证据**（探针 `248`/`249`/`251`，DEV 客户端）：罐 8 单位 victus 在机器正下方，拨 2 单位单次模式，手推
  12 tick：`suctionDown=128`、抽走 2 单位（罐余 6）、注射器消耗、输出注射剂 regen amp0 dur1200（2×权重12→
  时长 1200）、颜色 `de0005`（victus 色）、其余 8 格空、模式回 0。真包路径右键打开 `BloodInfuserScreen`，
  网格画出玩家 6 个已发现要素（截图 `blood_infuser_gui.png`，网格区 686 个高亮像素），机身 BER 截图
  `blood_infuser_block.png`。
- **探针环境的坑**：用 `setHealth` 强行复活死掉的测试员会让客户端的 OWNER 同步对象状态（TC4R
  `player_knowledge`）丢失，`PlayerKnowledgeApi.view` 在客户端抛 `IllegalStateException`，菜单打开即被关——
  重启客户端即恢复正常；屏幕的 `refresh()` 对该异常退化为空网格，避免真机玩家在同步落位前打开菜单炸包。
#### 遗留审计补测（同日）

交卷后对 Seroconverter 做了一轮遗留审计，补齐八条此前未覆盖的路径（探针 `263`/`266`）：

- **运行时配方**：`isorropia:infusion/blood_infuser` 在 RecipeManager 里就位（类型 `thaumcraft:infusion`）。
- **按钮协议（服务端真菜单驱动）**：负数 id 加拨已知表第 `(-id)-1` 个、`3+idx` 删、0–2 切模式，语义与 TH 一致。
- **自动模式**：同一表盘连做两支注射剂，模式保持 2；逐 tick 观察为「吸满→下一 tick 产出→acquired 清空→继续等下一支」。
- **输出满闸**：3×3 放满时完全不吸（currentlySucking=null、acquired 不动、注射器原样保留）。
- **推入路径**：管道直接 `addEssentia` 进机器（EXECUTE，非顶面）也计入 acquired。
- **shift 移动**：输出架→背包、热键栏注射器→0 号槽、圆石拒入（quickMoveStack 返回空）。
- **破坏掉落**：`onRemove` 把容器里的东西全部落地，BE 移除。
- **NBT 往返**：审计抓到**唯一真 bug**——tile 存档用 `SimpleContainer.createTag()`（紧凑列表、无 Slot 索引），
  而 `fromTag` 按列表位置回填，空槽塌缩会让 0 号之后的物品整体左移（探针里 2 号槽的注射剂漂到 1 号）。
  改成手写带 `Slot` 字节的存取（`saveItems`/`loadItems`），存档与 `getUpdateTag` 同步两路都换；修复后往返
  逐槽相等，248 探针回归无变化。

界面内的鼠标命中几何（网格格位、圆点、抽屉悬停）仍只有像素级证据，没有模拟点击的探针——坐标是从 TH 原版
1:1 抄的，风险低，留给下次开服顺手人眼复核。
