const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType,
  PageNumber, LevelFormat
} = require("docx");

const FONT = "Microsoft YaHei";
const CONTENT_WIDTH = 9026; // A4 (11906) minus 2 x 1440 margins

const border = { style: BorderStyle.SINGLE, size: 1, color: "BFBFBF" };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

function headerCell(text, width) {
  return new TableCell({
    borders, margins: cellMargins, width: { size: width, type: WidthType.DXA },
    shading: { fill: "2E75B6", type: ShadingType.CLEAR },
    verticalAlign: "center",
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ text, bold: true, color: "FFFFFF", font: FONT, size: 19 })]
    })]
  });
}

function cell(text, width, opts = {}) {
  const runs = Array.isArray(text) ? text : [text];
  return new TableCell({
    borders, margins: cellMargins, width: { size: width, type: WidthType.DXA },
    shading: opts.fill ? { fill: opts.fill, type: ShadingType.CLEAR } : undefined,
    verticalAlign: "center",
    children: runs.map(t => new Paragraph({
      alignment: opts.center ? AlignmentType.CENTER : AlignmentType.LEFT,
      children: [new TextRun({ text: t, font: FONT, size: 18, bold: !!opts.bold, color: opts.color || "262626" })]
    }))
  });
}

function makeTable(widths, headers, rows, zebra) {
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: widths,
    rows: [
      new TableRow({ tableHeader: true, children: headers.map((h, i) => headerCell(h, widths[i])) }),
      ...rows.map((r, ri) => new TableRow({
        children: r.map((c, ci) => {
          const conf = typeof c === "object" && c !== null && !Array.isArray(c) ? c : { text: c };
          return cell(conf.text, widths[ci], {
            center: conf.center !== undefined ? conf.center : ci === 0,
            bold: conf.bold, color: conf.color,
            fill: zebra && ri % 2 === 1 ? "F2F7FC" : undefined
          });
        })
      }))
    ]
  });
}

function heading(text, level) {
  return new Paragraph({ heading: level, spacing: { before: 320, after: 160 }, children: [new TextRun({ text, font: FONT })] });
}

function body(text, opts = {}) {
  return new Paragraph({
    spacing: { after: 120, line: 320 },
    children: [new TextRun({ text, font: FONT, size: 21, color: "404040", bold: !!opts.bold })]
  });
}

function bullet(text, boldPrefix) {
  const runs = [];
  if (boldPrefix) runs.push(new TextRun({ text: boldPrefix, bold: true, font: FONT, size: 21, color: "262626" }));
  runs.push(new TextRun({ text, font: FONT, size: 21, color: "404040" }));
  return new Paragraph({ numbering: { reference: "bullets", level: 0 }, spacing: { after: 100 }, children: runs });
}

const star = (n) => "★★★★★".slice(0, n) + "☆☆☆☆☆".slice(0, 5 - n);
const D = "D54937"; // red for warnings

// ---------- Table 1: implemented actions ----------
const t1w = [1000, 1350, 1750, 2200, 2726];
const t1 = [
  [star(5), "移动光标", "内部（悬浮层）", "G01 食指", "整个产品的地基，唯一连续动作，固定不换绑"],
  [star(5), "点击", "无障碍注入", "G02 食指弯曲伸直", "与光标配对的核心交互，触发自然"],
  [star(5), "向上/向下滚动", "无障碍注入", "G03/G04 食指挑动、G05/G06 四指并拢挥动", "刷视频/读文章最高频操作，四组手势冗余覆盖"],
  [star(5), "返回", "全局动作", "G07 四指左挥、G09 食指左移、G15 兰花指", "Android 使用频率第一的命令"],
  [star(5), "回桌面", "全局动作", "G08 四指右挥、G10 食指右移", "频率仅次于返回"],
  [star(5), "播放/暂停", "媒体按键", "G22 握拳", "隔空控媒体是本产品最强使用场景（手机支架场景）"],
  [star(4), "确认（点击光标处）", "无障碍注入", "G21 OK", "无障碍用户友好；与 G02 点击功能重叠，但 HOLD 姿势更适合手抖用户"],
  [star(4), "截图", "全局动作（API 28+）", "G13 张掌→握拳→张掌", "三段序列防误触设计好，但系统下拉快捷方式已很方便"],
  [star(3), "最近任务", "全局动作", "G14 莲花指", "中频；莲花指识别待真机校准"],
  [star(3), "自拍", "内部（抓帧存相册）", "G11 V 字", "招牌演示功能，实用性一般（前置摄像头本来就对着你）"],
  [{ text: "双击点赞", color: D }, "双击点赞", "无障碍注入（固定区域）", "G12 比心、G20 大拇指", { text: "营销亮点但最脆弱：固定屏幕坐标双击，换 App/换布局就失效", color: D }],
  [{ text: "滚动长截图", color: D }, "滚动长截图", "内部（已实现未映射）", "无（captureRollingScreenshot 为死代码入口）", { text: "代码已写完，接入动作目录零成本，建议激活", color: D }]
];

// ---------- Table 2: Android system command candidates ----------
const t2w = [1000, 1650, 2350, 1200, 2826];
const t2 = [
  [star(5), "下拉通知栏", "GLOBAL_ACTION_NOTIFICATIONS", "极低（一个常量分支）", "高频刚需，隔空操作通知的入口"],
  [star(5), "音量 + / −", "AudioManager.adjustStreamVolume", "低", "G18/G19 画圈手势的天然目标，媒体控制场景闭环"],
  [star(4), "上一曲 / 下一曲", "媒体按键", "低", "与播放/暂停构成完整媒体控制组，配支架场景"],
  [star(4), "锁屏", "GLOBAL_ACTION_LOCK_SCREEN（API 28+）", "极低", "单手关屏刚需，误触代价小"],
  [star(4), "唤起语音助手", "ACTION_VOICE_COMMAND 意图", "低", "\u201c手势→语音\u201d接力，单手操作效率翻倍"],
  [star(3), "快捷设置面板", "GLOBAL_ACTION_QUICK_SETTINGS", "极低", "开关 WiFi/手电筒入口，中频"],
  [star(3), "打开指定 App", "意图 + 包名", "中（G16/G17 规划项）", "需要应用选择器 UI 和持久化，规划中"],
  [star(3), "长按", "无障碍注入（单笔划 500ms）", "低", "呼出菜单/多选，交互刚需"],
  [star(3), "暂停识别", "内部", "中（G23 规划项）", "安全阀功能：手忙不过来时快速挂起识别"],
  [star(2), "关闭通知栏/面板", "GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE（API 30+）", "极低", "只作为\u201c下拉通知栏\u201d的配对撤销动作"],
  [star(2), "双指捏合/张开", "无障碍注入（双笔划）", "中", "地图/相册缩放，需要缩放参数设计，小众"],
  [star(2), "拖拽", "无障碍注入（长时程笔划）", "中", "滑块/接电话，与光标联动复杂"],
  [star(2), "静音", "AudioManager", "低", "音量键的附属品"],
  [star(2), "停止/快进/快退", "媒体按键", "低", "低频，歌曲切换场景用得上快进"],
];

const doc = new Document({
  styles: {
    default: { document: { run: { font: FONT, size: 21 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 30, bold: true, font: FONT, color: "1F3864" },
        paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 25, bold: true, font: FONT, color: "2E75B6" },
        paragraph: { spacing: { before: 300, after: 160 }, outlineLevel: 1 } }
    ]
  },
  numbering: {
    config: [
      { reference: "bullets",
        levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 620, hanging: 320 } } } }] }
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 11906, height: 16838 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [
          new TextRun({ text: "魔法手势功能目录 · 第 ", font: FONT, size: 16, color: "808080" }),
          new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: "808080" }),
          new TextRun({ text: " 页", font: FONT, size: 16, color: "808080" })
        ]
      })] })
    },
    children: [
      new Paragraph({
        alignment: AlignmentType.CENTER, spacing: { after: 80 },
        children: [new TextRun({ text: "魔法手势 Android V1.0", bold: true, font: FONT, size: 40, color: "1F3864" })]
      }),
      new Paragraph({
        alignment: AlignmentType.CENTER, spacing: { after: 360 },
        children: [new TextRun({ text: "功能目录与推荐星级（筛选稿）", bold: true, font: FONT, size: 28, color: "2E75B6" })]
      }),
      body("整理日期：2026-10-01　|　适用工程：MagicGesture-v0.9（映射已可配置化，提交 46b11ac）", {}),
      body("星级依据：单手高频刚需 > 误触代价 > 链路可靠性 > 机型兼容。已实现的动作现可通过\u201c校准页 → 手势动作映射\u201d换绑给任意 17 个手势（G02-G15、G20-G22）。红色条目为筛选时需重点关注项。", {}),

      heading("一、已实现功能（13 个动作，随时可换绑）", HeadingLevel.HEADING_1),
      makeTable(t1w, ["星级", "功能", "执行通道", "当前绑定手势", "推荐理由 / 风险"], t1, true),

      heading("二、Android 系统命令候选（未实现，接入成本低）", HeadingLevel.HEADING_1),
      body("更新（2026-10-01）：五项优先候选（下拉通知栏、音量 +/−、上一曲/下一曲、锁屏、语音助手）已全部接入动作目录，共 7 个新动作，出现在换绑选单中，默认映射保持不变。", { bold: true }),
      makeTable(t2w, ["星级", "功能", "执行通道", "接入成本", "推荐理由 / 风险"], t2, true),

      heading("三、筛选时建议考虑的约束", HeadingLevel.HEADING_1),
      bullet("可换绑手势 17 个（G02-G15、G20-G22），加上规划中的 G16-G19/G23 共 23 个。五星动作约 12 个，名额刚好，低星功能只能挤占冗余位。", "手势名额有限："),
      bullet("G07/G09/G15 都是返回、G08/G10 都是桌面、G12/G20/G02/G21 本质都是\u201c点一下\u201d——换绑自由化后，这些冗余手势位可以释放给音量、通知栏等新动作。", "当前有 3 处功能重叠可精简："),
      bullet("固定坐标双击不看执行结果，与开发规格\u201c失败不伪装成功\u201d的底线有张力，建议降级为实验性功能或淘汰。", "双击点赞（G12/G20）是唯一\u201c可能伪装成功\u201d的动作："),
      bullet("通知栏、音量、上下曲、锁屏、语音助手五项每项只需在 ControlAccessibilityService 加一个分支，即可立即出现在换绑选单里。", "建议优先补入动作目录的 5 项（四星以上 + 接入成本低）："),

      heading("四、筛选结论", HeadingLevel.HEADING_1),
      body("已确认（2026-10-01）：以下两项功能淘汰，不进入动作目录，后续版本也不再作为候选。其余条目待继续筛选。", { bold: true }),
      makeTable([1000, 2600, 5426], ["处置", "功能", "淘汰原因 / 备注"], [
        [{ text: "淘汰", color: D, bold: true }, "电源长按对话框", "误触代价最高（关机/重启入口），不适合手势触发（GLOBAL_ACTION_POWER_DIALOG）"],
        [{ text: "淘汰", color: D, bold: true }, "应用抽屉", "有上滑手势的机型已很方便，价值低且 API 31+ 才可用（GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS）"],
        [{ text: "新增接入", color: "2E75B6", bold: true }, "下拉通知栏", "已接入（2026-10-01）：GLOBAL_ACTION_NOTIFICATIONS，无 API 门槛"],
        [{ text: "新增接入", color: "2E75B6", bold: true }, "音量 + / 音量 −", "已接入（2026-10-01）：经 AudioManager 派发音量键，G18/G19 画圈手势仍按规划另做连续会话"],
        [{ text: "新增接入", color: "2E75B6", bold: true }, "上一曲 / 下一曲", "已接入（2026-10-01）：媒体按键，与播放/暂停构成完整媒体控制组"],
        [{ text: "新增接入", color: "2E75B6", bold: true }, "锁屏", "已接入（2026-10-01）：GLOBAL_ACTION_LOCK_SCREEN，Android 9+ 生效"],
        [{ text: "新增接入", color: "2E75B6", bold: true }, "语音助手", "已接入（2026-10-01）：ACTION_VOICE_COMMAND 意图，不读取任何页面内容"],
        ["保留", "（其余候选待继续筛选）", ""]
      ], false)
    ]
  }]
});

Packer.toBuffer(doc).then(buffer => {
  const out = process.argv[2] || "/../docs/手势功能目录与推荐星级_2026-10-01.docx";
  fs.writeFileSync(__dirname + out, buffer);
  console.log("OK");
});
