# 验证方法与实测记录

本工程的规矩：**编译通过不算完成，数据校验通过也不算完成**——TC 的集成点（注册、多方块、注魔、研究、渲染）
只有在跑起来的游戏里才能证伪。下面是可复用的验证手段，以及它们实际抓到过的缺陷。

## 一、验证手段

### 1. 快速层：`gradlew build`

编译 + JUnit。现有测试覆盖纯逻辑（治疗缸几何、矿猪掉落权重、果冻 NBT 往返、要素 NBT 编解码）。
纯 JUnit **不得触碰注册表**：`BlocksIS` 之类的 `DeferredRegister` 在无 Forge 环境下初始化会抛
`ExceptionInInitializerError`，所以测试只测纯函数。

### 2. 数据层：`python tools/validate_tc_data.py`

静态校验所有 TC 数据：研究条目字段与父研究闭合、配方引用、要素 id、物品 id（对照 TC4R jar 的注册名）、
注魔数据（`data/isorropia/isorropia/{creature,self}_infusions`：kind/organ/组件/要素/research/hook/modifiers 的属性 id）、
语言键与研究的对应关系。改数据后必跑。

### 3. 服务端实机：Rosetta 桥 + `probes/server/*`

`H:\MinecraftMods\RainJava-work\RosettaRemoteDebugBridge` 可以在**运行中的游戏里执行任意 Java 方法体**，
通过 127.0.0.1 的 TCP 控制面（token 认证）。用法：

```bash
# 1) 用 dev 映射的 jar（reobf 过的 jar 在 dev 环境会 AbstractMethodError）
cd ../RainJava-work/RosettaRemoteDebugBridge && ./gradlew.bat jar -x reobfJar --no-daemon
cp build/libs/rosetta_remote_debug_bridge-1.0.0.jar ../../Isorropia-1.20.1/run-server/mods/rosetta_bridge_dev.jar

# 2) 开服（run-server/ 下要有 eula.txt 与 server.properties；RCON 建议打开）
./gradlew.bat runServer

# 3) 跑探针：token 在 run-server/RosettaRemoteDebugBridge/remote-token.txt，端口 48790
T=$(cat run-server/RosettaRemoteDebugBridge/remote-token.txt)
python ../RainJava-work/RosettaRemoteDebugBridge/legacy/tools/rosetta_remote.py --port 48790 --token "$T" \
    exec "$PWD/probes/server/01_environment.java"
```

探针是**方法体**，必须 `return` 一个字符串（返回内容会打印）。

| 探针 | 检查 |
|---|---|
| 01_environment | 模组列表、我们注册的方块/物品/实体/BE、要素、研究条数、注魔数据条数 |
| 02_grant_and_form_vat | 用 admin context 授予研究；搭框架并成型治疗缸（原点写在 `run-server/rosetta_probes/vat_origin.txt`） |
| 03_curative_wired | jar → 管道 → 管道 → 缸的供料，缸内一头受伤的猪 |
| 04_curative_check | 03 之后约 30 秒跑：猪被治好、jar 的 victus 减少 |
| 05_vat_state | 缸的状态快照 |
| 06_creature_infusion | 基座 + 猪 + 要素 → 生物注魔完成、基座被消耗 |

### 4. 客户端实机：`python tools/client_probe.py`

客户端的工作目录是 `run-client/`，桥端口 **48791**（与服务端分开，可同时跑）。桥只在**进入世界**后才监听
（`ServerStartedEvent`），所以客户端用 `-PquickPlay=<世界>` 直接进存档。

```bash
python tools/client_probe.py start                 # 复制探针世界（若缺）→ 启动客户端 → 等桥就绪
python tools/client_probe.py exec probes/client/22_matrix_scene.java
python tools/client_probe.py shot vat_quads 64.5 302.5 58.0 0 28 4        # 世界截图
python tools/client_probe.py shot lens_wheel 64.5 300 60 0 0 1 --gui      # 连 HUD 一起截
python tools/client_probe.py guishot fingers_by_key                       # 原样截当前打开的界面（不动镜头）
python tools/client_probe.py stop                  # 优雅关闭客户端
```

`shot` 分两步：先在**服务端线程**把玩家传送/转向并强制 `ChunkSource.move()`，等若干秒后**在渲染线程**重新
设定朝向再 `Screenshot.grab`（同一帧内改姿态，避免鼠标漂移）。截图落在 `run-client/screenshots/`。

**HUD 进不进截图，取决于抓帧时的 `options.hideGui`**：Forge 在 `hideGui`（F1）为真时跳过所有
`RegisterGuiOverlaysEvent` 的绘制，而探针默认把它置真以免 HUD 挡住世界图，所以不加开关时截图**只有世界**。
`shot ... --gui` 会在抓帧前把它置回假，物品栏与自建 HUD（透镜轮盘、要素读数、嫉妒进度条）都会进图；
世界内渲染（`AFTER_LEVEL` 里的涟漪/名牌）两种模式都在。所以 HUD 像素也能截图验收，不必只靠人眼。

| 客户端探针 | 检查 |
|---|---|
| 25_lens_wheel | 装片后轮盘确实每帧在画（`LensWheel.isOpen()/hasDrawn()`） |
| 26_brain_equip | 躯体大脑装进 Curios 头部槽，第三视角截图看落点 |
| 27_lens_air | 基里安透镜：身前几只生物，截图看涟漪/漩涡 |
| 31_lens_entities | 列出会画涟漪的活物及距离（排查「为什么没涟漪」） |
| 32_lens_ordo | 学者透镜：正前方一头牛，看名牌与要素读数 |
| 33_lens_envy | 嫉妒透镜：给牛灌一半嫉妒，看右下进度条 |
| 36_block_aspects2 | 对比 `block(state)` 与 `placedBlock(stack,state)` 的要素差（原版方块只有后者非空） |
| 43_lens_drop_probe / 44_lens_ordo_drop2 | 学者透镜读准星下方块上的掉落物（名牌+要素走物品） |
| 45_fingers_open / 46_fingers_recipe / 53_fingers_take | 变形手指：长出手指并直接开界面、在格子里摆基础透镜配方、潜行取出成品并核对扣费 |
| 47_beacon_setup → 48_beacon_kill → 51_vat_exit | 灵魂信标：成型治疗缸、放好待命身体并绑定，然后致死一击；应当活着、解绑、只剩新身体的能力、被缸收着；再右键出缸，客户端与服务端坐标都在缸顶上方 |
| 49_keys → 50_fingers_key_check | 走真实按键路径：J 按住开轮盘/松开关轮盘，M 打开便携工作台；并检查两个键各只注册一次 |
| 52_containment_hold → 53_containment_check | 收容焦点：生存模式、满充能法杖装上焦点、一个空守卫罐、残血猪；按住右键（真实按键路径），之后检查猪进了灵魂罐、守卫罐被消耗、无罐后自动停手、vis 被扣 |
| 54_containment_visual | 同上但猪满血，用来截光束（`shot` 会切创造模式，创造模式不耗罐） |
| 55_containment_rules → 53 | 凋灵免疫、铁傀儡可收；无空罐时光束立刻停、不扣 vis、猪还在 |
| 56_matrix_flow | 改造矩阵：法杖第一下开缸、无可注魔时不关缸、拆矩阵则停缸且启动量归零（缸仍成型）；末尾戴好护目镜，可接 `shot matrix_readout ... --gui` 看稳定度读数 |
| 57_soul_jar_flow | 灵魂罐：手持空守卫罐右键缸把生物装回罐（罐名随内容、不带缸里的无 AI 标记）、满罐右键缸放回并退还守卫罐、法杖开放置的罐子生物出现在罐子原位 |
| 58_vat_effects | 接在 56 之后：改 `EFFECT` 为 aura/vent/burst，在客户端打开对应特效并数粒子引擎里的 `GenericFx`；**缸的渲染器只在缸在画面里时运行**，先用 `shot` 把镜头对准场景再触发 |
| 59_gravekeeper_beam | 守墓者与一只静止僵尸相距 3 格，配合注释里的 `shot` 命令看光束 |
| 60_vat_blueprint | 授予治疗缸研究，在客户端直接打开百科到蓝图页（20732 里百科是基于研究快照的客户端界面，不走服务端菜单），之后 `guishot vat_blueprint` 截图 |
| 84–88 | 魔力发电机（CodeBuddy 写的）：法杖按住充能、六原质各供 5 cV、中继抽取、松手停供 |
| 89_essentia_dynamo → 90 | 精华发电机：罐（32 火）上放发电机、旁边放 vis 中继；检查罐在减少、每 tick 供 12 火、中继先抽到 12 再抽为 0；顺带检查复合要素拆原质（生命→地+水，人类→六原质） |
| 91–95 | 节点监视器：在 20 风 20 火的节点顶面安装；健康时无信号，把火降到 1 后 15 级强信号；拆掉节点后监视器自己掉成物品 |
| 96_shard_trans | 碎片嬗变：六个沸腾坩埚各投入一颗平衡碎片，分别用六原质之一 + 一点置换要素，各自产出对应原质碎片（`made=6/6`） |
| 97_alchemite → 98 | 炼金炸药：一圈石头 + 八个灵气节点中点一颗短引信炸药；石头碎成 terra 结晶、不掉石头物品、炸药块被炸掉、八个节点无一保持原样 |
| 99 → 100 | 炼金炸药的发射器行为：通电发射器把炸药放到正前方一格且已点燃（检查完立刻移除，不让它炸） |
| 101_ethereal_shard | 以太碎片：喂三颗风之精华后容量 12、精华被吃掉；旁边放 vis 中继与充能节点，预存 5 点风 vis；给玩家一把空法杖并转身面向碎片 |
| 102 → 103 | 以太碎片抽取：网络充能后客户端也看到存量增长；按住真实右键后法杖进账、碎片扣减；掉落计算返回三颗风之精华（此处只调用掉落方法，真实拆除见 104） |
| 104_ethereal_shard_edges | 拒绝普通物品/结晶扩容；NBT 往返保留容量和存量；耗尽要素仍为 0、监视器告警；真实拆除返还 130 风精华 + 1 火精华 + 1 碎片方块，所有堆叠合法、同方块更新不提前掉落 |
| 105 → 106 → 107 | 先对空仓按住法杖，稍后存入 3 点风 vis，无需重按即可到账 300 cV、碎片余量归零 |
| 108_ethereal_shard_visual | 摆放空白/风/火/水/混合五种晶体与手持物品，用于截图检查模型、容量混色、轨道符标及 BEWLR |
| 109 → 110 | 两个相隔 24 格的网络分别提供地+水、仅地；生命要素只有前者能充能；另验严格 `>100` 边界与小数存量 NBT 往返 |
| 111 → 112 → 113 | 长时间抽取截图场景：256 风 vis 与大容量空杖；检查两端抽取状态、客户端持杖状态和颜色，再用 `guishot` 保持视角截图；113 捕获 BER 实际发出的晶体/符标/光带顶点与 RGB（不列入自动回归） |
| 114 → 115 → 116 | 魔力发电机选择界面：空手右键开菜单、服务端按钮关 ignis 后充能脉冲不收也不供 ignis（aer 照常 40/5）；客户端界面显示 ignis 为暗、真实点击 aer 图标经按钮包关掉 aer，并经数据槽同步回界面 |
| 117 → 118 → 119 → 120 | 法杖按住机器时焦点不运行：先装火焰焦点对空按住作对照（实测 ignis 715 cV、aer 0，证明焦点确实在烧），再按住发电机，ignis 与 aer 只被发电机按 40 cV/脉冲等量扣（实测各 760）；之后转开视线，两端都释放按住 |
| 121_illumination_dye | 照明焦点染色：新焦点为 11（黄）、加红染料得 1 且名称键为 `.1`、多个染料取最后一个（白＝15）、混入泥土或两个焦点都不成立 |
| 122 → 123 | 光珠：5 色光珠方块均不渲染自身、均带 `TileLight`；数秒后客户端粒子引擎里在光珠处有 TC4 wisp（实测 51 颗） |
| 124 → 125 | 收容焦点 slow 升级（第 2 阶）：满血猪被光束命中后带缓慢 0 级，且未被装罐 |

**探针体跑在服务端线程上**（`Thread.currentThread()` 是 `Server thread`）。所以凡是要服务端回话的操作——
客户端发包、服务端开界面、关界面——在探针返回之前都不会发生：探针里等多久都看不到结果。做法是拆成两个探针，
前一个只「按下」，后一个再「看结果」（见 49_keys → 50_fingers_key_check）。纯客户端的状态（轮盘开合）可以在
同一个探针里轮询等结果，但要**等结果而不是固定睡眠**：后台窗口帧率低，同一帧里排进去的「按下」和「松开」
会在任何一个 tick 看见之前就抵消掉。

### 4b. 一键回归：`python tools/client_sweep.py`

在**一次**客户端会话里按依赖顺序跑完所有自洽的客户端探针，逐个检查输出里必须出现的事实（如「J held: wheel=true」
「holds=minecraft:pig」「vatHoldsPlayer=true」），逐行打印 PASS/FAIL，有失败时退出码为 1。需要服务端回应的步骤后面自动停顿再接检查探针。
可以只跑一部分：`python tools/client_sweep.py 52 57`（按探针名前缀）。改动较大后先跑它，再看截图。

Windows 上父子 Python 进程统一使用 UTF-8：回归脚本为子进程设置 `PYTHONIOENCODING=utf-8`，并用
`encoding="utf-8"` 读取输出。仅设置环境变量而仍按系统 GBK 解码，会在中文物品名处触发
`UnicodeDecodeError`，这是测试工具中断，不是玩法断言失败。

### 4c. 以太碎片批次记录（2026-10-12）

- `build/shard-delivery-build.log`：构建成功，JUnit 13 项、0 失败/错误；数据校验 48 研究、46 配方、0 错误，保留 `#forge:crops` 的既有警告；26 个事件监听器、0 撞名。
- `build/shard-stability-final.log`：97/98 与碎片功能组共 11 步通过，101–103 再连续重复两轮均通过。
- `build/shard-acceptance-full-sweep.log`：修复后完整 **82 步全部通过，退出码 0**；62 步有行为断言，20 步仅作无运行错误的冒烟检查。客户端日志 ERROR/FATAL 为 0，但仍有既有的 cat 物品缺贴图与开发桥兼容警告。本轮未重跑专用服务器探针，不代表所有玩法/组合或旧版逐像素外观均已覆盖。
- 101/102 的连续回归隔离：先释放双端持物状态、关闭旧容器、重设生存飞行标志与快捷栏，再在客户端预置同一空杖副本；点击仍走真实按键与网络路径，不直接向法杖注入测试结果。
- **装罐回归（2026-10-30，探针 44–46，1.20.1 与 1.21.1 各跑一遍）**：3×4×3 玻璃 + 木台阶围住充能碎片后，`fits=true`、
  `tryForm=CONSUME`，方块变 `thaumcraft:node_jar`，罐内 `aer=5`（base 也是 5）、`instanceId` 已生成、外壳被吃干净、
  附近掉落 **0** 件（不会既进罐又掉精华）；再用杖点罐，`releaseNode` 放出 `thaumcraft:aura_node`，`aer=5/5`、类型
  `normal`、可继续抽取。空白碎片同样能装罐，罐里无 vis 但带 id，放下不会重新生成一个满节点。
  与旧版（TC4R 20721）的差别：那版的 `NodeJarBlock.fits` 只认 `AuraNodeBlockEntity`，碎片根本进不了罐；20753 改成
  `NodeApi.view` + `#thaumcraft:node_jar_hosts` 标签，碎片实现 `AuraNodeView` 且已在标签里，所以现在能装。
  仍未保留的是**容量**：罐里 base 取的是存量 vis，喂进去的容量（精华）在装罐时蒸发，破坏反而更划算。
- 111–113：服务端/客户端均 `draining=true`、客户端正在使用法杖；捕获到晶体 24、符标 4、光带 128 个顶点。
- 空白/风/火/水/混合晶体的实际顶点 RGB 分别为 `FFFFFF`、`FFFF95`、`FF6E09`、`4BF6FF`、`CCC7BC`；不是只验证计算函数，而是捕获 BER 实际输出。
- 截图保存在本地 `run-client/screenshots/shard_verified_beam.png`、`shard_verified_colours.png`；不作为旧版本逐像素等价证明。
- 炼金炸药探针 98 曾漏检：爆炸移除试验体地板后，结晶掉出原采样框；扩大垂直排查已在世界下方找到结晶。97 改为在试验体下方放置不可炸毁的接物平台并清理旧掉落，98 的检查框覆盖平台；保留 `essence=true` 断言。
- 不要把 Gradle 构建和 Rosetta 动态编译探针并行：Gradle 更新 class 输出时，探针可能暂时无法解析已有类（本轮见 `BlocksIS cannot be resolved`），从而连带破坏后续检查。

### 4d. 已移植内容收尾批次记录（2026-10-13）

- 新增探针 114–125 在同一客户端会话中一次通过；启动日志有 `Wand use tick hook installed`（`WandItemMixin` 已注入 TC4R 的 `WandItem.onUseTick`）。
- 截图（本地 `run-client/screenshots/`）：`batch2_vis_dynamo_gui.png`（ignis 关时为暗，其余彩色）、`batch2_light_wisps.png`（光珠仅有 wisp；
  红、蓝保留色相，黄、橙在加色叠加下接近白色——与 TH 实机的对照尚未人眼确认）。
- 焦点抑制的结论只覆盖内置火焰焦点；电击/挖掘与附属焦点走同一入口（整段 `onUseTick` 被跳过），但未单独实测。
- 全量回归里 `65_liquefaction_smelt` 偶发「矿已熔、找不到铁锭」：63 的冰融成水后在平台上扩散（实测 26 格），64 换上铁矿后熔出的铁锭被水流冲走，
  曾被冲到平台边缘（z≈45.2，平台到 z≈46），再远就离开检查框或掉下平台；上一轮的铁锭也会留在原地。64 现在先断言冰已化水、再排干平台残水，
  63 开始前清理旧掉落；断言不变，修正后 63→65 连续 4 轮通过。
- `build/batch2-final-build.log`：构建成功，JUnit 13 项通过；数据校验 48 研究、47 配方、0 错误（保留 `#forge:crops` 既有警告）；27 个事件监听器、0 撞名。
- `build/batch2-full-sweep-2.log`：完整回归 **94 步全部通过**（74 步有行为断言、20 步仅冒烟），客户端日志 ERROR/FATAL 为 0。
  修正前的一次全量（`batch2-full-sweep.log`）为 93/94，唯一失败即上面的 65。本批未重跑专用服务器探针。

### 5. 网格对照：`python tools/mesh_preview.py`

把 OBJ + 贴图做一次软件光栅化渲染，用来在**不进游戏**的情况下判断「三角形和 UV 是不是我以为的样子」。
治疗缸的 OBJ 就是这么定案贴图朝向与形状的；游戏渲染与预览图不一致时，问题一定在渲染状态而不是数据。

```bash
python tools/mesh_preview.py src/main/resources/assets/isorropia/models/block/vat_redo.obj \
    src/main/resources/assets/isorropia/textures/block/curative_vat.png /tmp/vat_preview.png \
    --camera "0,5.5,-6.5" --look "0,2,0"
```

### 6. 事件处理器撞名：`python tools/check_listener_names.py`

Forge 的 EventBus 把每个 `@SubscribeEvent` 方法编译成一个类，类名只由
「包名 + 声明类的简单名 + 方法名 + 事件简单名」拼成，并按这个名字缓存。两个处理器拼出同一个名字时，
后注册的会**复用**前一个的类：前一个跑两次，后一个一次都不跑，而且没有任何报错。嵌套类最容易中招
（两个不同外部类里的 `Register.onRegisterKeys` 在同一个包里就撞了）。加了事件处理器就跑一次，有撞名时退出码 1。

### 7. 服务端优雅停服

```bash
python tools/rcon.py stop        # run-server/server.properties 里 RCON 密码为 isoropia
```

强制 kill 会跳过 `ServerStoppingEvent`，任何「存盘后仍在」的验证都作废。

## 二、实机抓到的缺陷（全部已修）

这些**没有一个**能在编译期或静态检查里发现，都是跑起来才暴露的：

| # | 现象 | 根因 | 修复 |
|---|---|---|---|
| 1 | 任何含治疗缸的区块在客户端加载失败，缸不可见 | 客户端用 `load()` 解析**同步用的精简 update tag**，里面没有 `Body` 字段，`CelestialBody.byName` 抛 `IllegalArgumentException` | 同步数据走独立读取器 `readSync()`；未知天体名回落 `NONE` |
| 2 | 注魔永远走不完 | 缸把要素存进 32 容量的缓冲，而配方要 50+，`essentiaStep` 永远等不到齐 | 注魔中的要素**直接抵扣需求**（上游 `addToContainer` 的语义），不再进缓冲 |
| 3 | 抽到要素后吸力不消失、管道持续灌料 | `demanded` 只增不减 | 每周期重算需求（`clearDemand`），只广告当前缺口 |
| 4 | 放生物进去后缸仍在「浸泡尸体」模式，不治病 | 模式是从多处状态翻出来的**记忆值**，会变陈旧 | 空闲模式改为每 tick 推导（有生物=治疗，有偶无生物=浸泡） |
| 5 | 注魔完成时刷屏 `Invalid message SyncLivingDataPacket` | 包类写了但从未注册 | `IsorropiaNetwork.init()` 注册 |
| 6 | 多方块渲染成一团乱三角 | 面按**三角形**写进了 `Mode.QUADS` 缓冲（每 4 个顶点被拼成一个四边形） | 每个面写满 4 个顶点（三角形重复末点） |
| 7 | 缸的正面整片消失、只剩背面 | 用的 `entityTranslucent` **带背面剔除**，而 OBJ 的绕序不可靠 | 自建 NO_CULL 半透明 RenderType（照 TC4R 的 shim 写法） |
| 8 | 灵魂信标的光柱不出现 | 天空检测用了信标**自身**那一格（被自己挡住）；增长系数乘在了贴图缩放而不是长度上 | 检测 `pos.above()`；`height = 256 × scale` |
| 9 | 受击反伤只对远程生效 | `getDirectEntity() instanceof LivingEntity` 的守卫同时也吃掉了近战 | 只对「远程」分支加守卫，反伤照所有攻击者 |
| 10 | 躯体大脑无处可戴 | TC4R 只声明了 `ring`、`necklace`、`belt` 三个 curio 槽，没有 `head` | 本模组自带 `data/isorropia/curios/{slots/head.json,entities/player.json}` |
| 11 | 嫉妒进度条永远空 | `EnvyLens` 把嫉妒累加到**玩家**的数据上，而 HUD 读的是**被看生物**的数据 | 计数改记在生物自己的 `LivingData` 上，再 `syncTo(player)` |
| 12 | 学者读数对方块显示「无要素」 | `AspectQueryApi.block(state)` 对原版放置方块返回空（它只认 datapack 声明的 block 要素） | 改走 `placedBlock(new ItemStack(state.getBlock()), state)`（原版也是问方块的**物品**要素） |
| 13 | 学者读数对地面显示「Air」 | 玩家悬空、准星 `hitType == MISS`，并非读数错误 | 探针先在脚下铺一块草方块再抓图；读数随即显示 "Grass Block" |
| 14 | M 键（变形手指）按了没反应；J 键注册了两次 | 两个按键类的嵌套处理器 `Register.onRegisterKeys`、`Poller.onClientTick` 在 EventBus 里**撞名**（见上文第 6 节），手指键的注册和轮询从未执行，透镜键的各执行两次 | 处理器改用不重复的方法名；新增 `tools/check_listener_names.py` 静态检查 |
| 15 | 灵魂信标复生后保留了旧身体的能力 | 复生判定放在了「旧身体死亡清算」之前，旧能力没被清掉、新能力叠加上去 | 照上游顺序：先还肿瘤扭曲、清空旧能力，再复生并只给新身体的能力；复生者照上游被缸收着，右键出缸 |
| 16 | 玩家右键出缸后又回到缸里（出不来） | 缸用 `entity.setPos` 挪动占用者；对玩家这只改了服务端坐标，客户端下一个移动包又把位置拉回去 | 玩家走 `connection.teleport`（`TileVat.place`），进缸与出缸都是 |
| 17 | 把死尸雕像放进缸里看不见雕像，但要素照抽、照常自我注魔 | `level.sendBlockUpdated(pos, 同状态, 同状态, …)` **不带 BE 数据**：`ServerLevel` 只把坐标塞进区块的方块广播队列，客户端 `mode` 一直停在区块加载时的 0，渲染器根本没进画尸体的分支（服务端一切正常） | `TileVat.sync()` 直接给跟踪该区块的玩家发 `ClientboundBlockEntityDataPacket` |
| 18 | 修好同步后雕像只有一条胳膊一条腿 | 皮肤是 64×32 布局，补画布到 64×64 时左臂/左腿槽位是空的，现代模型采样到透明 | 把右臂(40,16,56,32)/右腿(0,16,16,32) 水平镜像写进左臂(32,48)/左腿(16,48) 槽位 |
| 19 | 雕像小到看不见（0.15 格，两三个像素），且被水面盖住 | 上游把 `0.1` 传给 1.7/1.12 模型（顶点仍是 1/16 格），现代 `ModelPart` 已除以 16，同一个数字只剩十分之一格；另外尸体画在水之前，被 85% 不透明的水面盖住 | 缩放改 1.2（填满缸内两格水柱）；绘制顺序改为水→尸体→外壳 |
| 20 | 同一类「画面冻住」遍布另外 10 台机器：灵魂罐里的生物不出现、重组器的闪电环与光柱不亮、传送门漩涡永远黄、要素/灵气发电机不发光、灵化水晶的吸管不画、换质增幅器与漩涡稳定器不动作 | 第 17 条那个无效写法是**抄来的模板**，`TileBloodInfuser`、`TileEssentiaDynamo`、`TileEtherealShard`、`TileJarSoul`、`TilePortal`、`TileRecombinator`、`TileSlot`、`TileTransductionAmplifier`、`TileVisDynamo`、`TileVortex`、`TileVortexStabilizer` 全都一样：`getUpdateTag` 写得好好的，就是没人发 | 抽成 `TileSync.toWatchers(be)`（发给 `chunkMap.getPlayers`），11 处全部改调它；`TileSlot` 另有一条 `setBlock` 通路，但只有 `FILLED` 真的翻转时才带数据，所以也补发一次 |

## 三、踩过的坑（写下来省下一个人一天）

- **方块实体同步只有一条路**：`level.sendBlockUpdated(pos, 同一个状态, 同一个状态, UPDATE_CLIENTS)` **不发任何
  BE 数据**（`ServerLevel` 只是把坐标塞进区块的方块广播队列，而那条广播只在状态真的变了时才带 tag）。要同步就
  调 `TileSync.toWatchers(be)`。反过来，若状态确实变了（`setBlock`），广播会顺带把 tag 发出去，别再重复发。
- **`getUpdateTag` 现在会被频繁调用**：以前只在区块广播/存档时跑，改完后每次同步都跑一遍。写 tag 的代码不能
  解引用可能为 null 的字段，否则会在服务端 tick 里炸（`probes/client/37_sync_sweep.java` 逐个放置并触发一遍）。
- **伪玩家（FakePlayer）的研究每开一次服务器就没了**：它挂在能力上、不落盘。所有需要研究门槛的探针都要在
  **同一次会话**里先授予（见 06 的头部那段）。
- **句柄方向**：灵魂罐只从**上表面**出料（`isConnectable(face) == face == UP`），管道只**服务**不主动推。
  所以「罐 → 管 → 缸」必须是罐在下、管在上；缸要**主动抽**。贴错方向时表现为「什么都不发生」。
- **强制加载区块**：没有玩家在线时，`level.setChunkForced` 必须先做，否则缸和管道根本不 tick。
- **`getRenderBoundingBox` 在方块实体上**（1.20.1 的 `BlockEntityRenderer` 没有这个方法），BER 只覆写
  `shouldRenderOffScreen` / `getViewDistance`。
- **不要在 `common` 里碰 client 类**；BER/粒子都放 `client/`，服务端只发事件（见 `ClientFxPacket`）。
- **TC4R 会把所有命名空间的 `textures/models/` 拼进方块图集**，渲染器皮肤一律放 `textures/entity/`。
- **内部类的兼容点集中放置**：`common/compat/{ThaumBridge,TaintBridge,ScanBridge}` 是唯一允许引用 TC4R
  `*.registry/*.common/*.nativeimpl` 的地方，升级 TC4R 时先看这三个文件。
- **`System.out` 不进 `run-client/logs/*.log`**：开发环境里它只到控制台，也就是 `client_probe.py start`
  重定向出来的 `build/client-run.log`。在 `latest.log` 里找不到自己打的调试输出，不能说明那段代码没跑。
- **`shot` 会把玩家切成创造模式并飞行**（避免摔落），所以要测「生存模式才有」的消耗（罐子、物品），别在中间截图。
- **生存模式的探针要先设和平难度**：夜里开阔平台会刷僵尸，把玩家打下去摔死（"doomed to fall by Zombie"），后面的探针全读到一个死人。
- **服务端时间会「追帧」**：探针阻塞服务端线程后，服务端会连跑补帧，墙钟 0.5 秒能过 40 tick。按 tick 计的进度不要用墙钟估算。
- **短命粒子要在同一段客户端序列里「触发 → 等几帧 → 抓帧」**：`shot` 的放置与等待加起来一两秒，寿命 30–50 tick 的爆发早就散完了。
- **测试完记得停服/停客户端**：客户端 `python tools/client_probe.py stop`，服务端 `python tools/rcon.py stop`。
