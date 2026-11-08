#!/usr/bin/env python3
"""Writes the full review's inputs: list-N.txt (the kanji of batch N) and in-N.txt (for each kanji what the card
shows, every stroke grouped by the KanjiVG part it is in with its colour, and the flags a script can raise).

Usage: make_inputs.py <batches> <kanji to leave out (already in the outside review)>"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, os.path.join(HERE, "..", "..", ".."))
import build_parts_data as bp  # noqa: E402
from convert import load  # noqa: E402
from export_md import kosei_text  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "..", "app", "src", "main", "assets")
KANJIVG = "/tmp/claude-0/-home-user-test/1560e2f5-85e5-5883-ad85-16a26bf44f74/scratchpad/src/kanjivg/kanji"
# Simplified Chinese shapes that Japanese does not use, often slipped into stories.
CHINESE = set("电黾龟马鸟鱼贝车门见言纟钅饣讠闩长东乐专业丰书买乱争亏产亲仅从众优会传伤体余侠侣俭剑剂劝办务动势单卖卢历厅压县发变叶号吕吴园围国图圆圣场块坚坛坝垄垒执报墙壮声处备复头夸夹夺奋奖妆妇妈娱婴孙学宁宝实宠审宪宫宽宾对寻导寿将尔尘尝层岁岂岗岛峡币帅师帐带帮库应庙废开弃张弹强归当录彻径忆忧怀态怜总恋恶惊惧惩惫惭惯愤愿戏战户扑执扩扫扬扰抚抛抢护报拟拥择挂挡挤挥损换据掳揽搀摄摆摇敌数斋断无时显晓晕暂术机杀杂权条来杨极构枢枪柜栋栏树样桥梦检楼欢欧毁气汇汉沟没沪泪泽洁浅测济浑涨湾湿满滞漏灭灯灵灾炉点炼烂烦烧热爱爷牵犹狈独狭猎献环现画畅疗疮疯痒盐监盖盘着矶矿码砖础硕确碍礼祸离种积称稳穷窃窍竞笔笋筑签简粮紧纠红约级纪纤纯纱纲纳纵纸纹纺线练组细织终绍经结绕绘给络绝统绢继绩绪续维综绿缆缓编缘缩罗罚罢羡翘耸职联聪肃肠肤肾肿胀胁胜脉脑脚脱腊脸舰舱艳艺节芦苏苹范荣药莲获营萧蓝虑虽蚀蚁蛮蜡补衬袜装褛觉览触誉计订认讨让训议讯记讲讳许论设访证评识诉词译试诗诚话诞诡询该详语误说请诸诺读课谁调谅谈谊谋谓谢谣谦谨谱贞负贡财责贤败账货质贩贪贫购贯贵贸费贺贼贾资赋赌赏赐赔赖赞赠赵趋跃践踪轨轩转轮软轰轻载较辅辆辉辈辑输辞辩边达迁过迈运还这进远违连迟适选递逻遗邓邮邻郑酱释针钓钟钢钥钦钱铁铃铜铝银铸链销锁锅锋错锡键镇镜长闭问闯闲间闷闸闹闻阀阁阅队阳阴阵阶际陆陈险随隐难雾静韦韧页顶项顺须顾顿颁颂预领频颗题颜额风飞饥饭饮饰饱饼养馆驱驶驻驾驿骂验骑骚鸡鸣鸭鸿鹅鹤麦黄齐齿龙")


def strokes_line(ch, tags):
    svg = os.path.join(KANJIVG, "%05x.svg" % ord(ch))
    if not os.path.exists(svg):
        return "(KanjiVG 없음)", []
    top = bp.parse_svg(svg)
    paths, groups = [], []
    bp.walk(top, [top], paths, groups)
    runs, orphans = [], []
    for i, (d, ps) in enumerate(paths):
        named = [g.get(bp.KVG + "element") for g in ps[1:] if g.get(bp.KVG + "element")]
        where = ">".join(named) if named else "(부품 밖)"
        if not named:
            orphans.append(i + 1)
        tag = tags[i] if i < len(tags) else "?"
        if runs and runs[-1][1] == where and runs[-1][2] == tag:
            runs[-1][0].append(i + 1)
        else:
            runs.append([[i + 1], where, tag])
    text = "; ".join("%s %s [%s]" % ("%d" % r[0][0] if len(r[0]) == 1 else "%d-%d" % (r[0][0], r[0][-1]), r[2], r[1])
                     for r in runs)
    return text, orphans


def main(batches, leave_out):
    kanji, parts, origin = load(os.path.join(ASSETS, "kanji.tsv")), load(os.path.join(ASSETS, "parts.tsv")), \
        load(os.path.join(ASSETS, "origin.tsv"))
    joyo = [c for c, f in kanji.items() if len(f) > 6 and f[6].isdigit() and 1 <= int(f[6]) <= 8]
    jset = set(joyo)
    skip = set(open(leave_out, encoding="utf-8").read().split()) if os.path.exists(leave_out) else set()
    todo = [c for c in joyo if c not in skip]
    n = int(batches)
    size = -(-len(todo) // n)
    # Radical shapes are fair part names in a story (亅, 冖), as are all the parts KanjiVG names.
    named = set()
    for line in open(os.path.join(ASSETS, "radicals.tsv"), encoding="utf-8"):
        if line.strip() and not line.startswith("#"):
            named.add(line.split("\t")[1])
    for f in parts.values():
        for x in (f[4].split() if len(f) > 4 else []):
            named.update(x.split("/")[0:2])
    for b in range(n):
        chunk = todo[b * size:(b + 1) * size]
        with open(os.path.join(HERE, "list-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n".join(chunk) + "\n")
        out = []
        for ch in chunk:
            k, p, o = kanji[ch], parts[ch], origin.get(ch, [ch, "", "", ""])
            tags = [s[0] for s in p[5].split("|")] if p[5] else []
            spec = o[3] if len(o) > 3 else ""
            radical = "曰" if p[1] == "73" else p[2]
            line, orphans = strokes_line(ch, tags)
            flags = []
            plist = [x.split("/") for x in p[4].split()] if p[4] else []
            if orphans and (len(plist) >= 2 or spec):
                flags.append("부품 밖 획 %s" % ",".join(map(str, orphans)))
            memo = o[2] if len(o) > 2 else ""
            odd = [c for c in re.findall(r"\(([^()]+)\)", memo) for c in c
                   if '㐀' <= c <= '鿿' and c not in jset and c not in named]
            if odd:
                flags.append("외우기에 상용한자 밖 글자 %s" % "".join(dict.fromkeys(odd)))
            chinese = [c for c in memo + spec + o[1] if c in CHINESE and c not in jset]
            if chinese:
                flags.append("중국 간체자로 보이는 글자 %s" % "".join(dict.fromkeys(chinese)))
            if not spec and len([c for c in re.findall(r"[㐀-鿿]", o[1]) if c != ch]) >= 2 and len(plist) < 2:
                flags.append("구성 줄 없음(유래는 부품을 말함)")
            out.append("\n".join([
                "■ %s  훈음 %s | 음 %s | 획수 %s | 부수 %s %s" % (ch, k[10] or k[1], k[3], k[5], p[1], radical),
                "  구성: %s" % (kosei_text(spec, radical) if spec else "(없음)"),
                "  구성(저장): %s" % spec,
                "  유래: %s" % o[1],
                "  외우기: %s" % memo,
                "  획: %s" % line,
                "  신호: %s" % (" / ".join(flags) if flags else "-"),
            ]))
        with open(os.path.join(HERE, "in-%d.txt" % (b + 1)), "w", encoding="utf-8") as f:
            f.write("\n\n".join(out) + "\n")
        print("batch", b + 1, len(chunk), "kanji,", sum(1 for x in out if "신호: -" not in x), "with flags")


if __name__ == "__main__":
    main(*sys.argv[1:3])
