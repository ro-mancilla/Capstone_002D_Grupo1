#!/usr/bin/env python3
"""Flag candidate AI-writing tells in a text file (or stdin).

Usage:
    python scan_tells.py draft.md
    cat draft.txt | python scan_tells.py

Output is a list of flagged lines grouped by category, plus a short density
summary. Flags are prompts to reread, not verdicts. Fix a flagged line by
making it more specific, not by swapping in a synonym.
"""
import re
import sys
from collections import defaultdict

W = r"\b"

CATEGORIES = {
    "overused vocabulary": [
        r"^\s*additionally\b", r"\balign(s|ed|ing)? with\b", r"\bboast(s|ed|ing)?\b",
        r"\bbolster(ed|s|ing)?\b", r"\bcrucial\b", r"\bdeep dive\b", r"\bdelv(e|es|ed|ing)\b",
        r"\bemphasiz(e|es|ed|ing)\b", r"\benduring\b", r"\benhanc(e|es|ed|ing)\b",
        r"\bfoster(s|ed|ing)?\b", r"\bgarner(s|ed|ing)?\b", r"\bhighlight(s|ed|ing)\b",
        r"\binterplay\b", r"\bintricac(y|ies)\b", r"\bintricate\b", r"\blandscape\b",
        r"\bmeticulous(ly)?\b", r"\bpivotal\b", r"\brobust\b", r"\bshowcas(e|es|ed|ing)\b",
        r"\btapestry\b", r"\btestament\b", r"\bunderscor(e|es|ed|ing)\b",
        r"\bvaluable\b", r"\bvibrant\b",
    ],
    "inflated significance": [
        r"\b(stands|serves) as\b", r"\bis a (testament|reminder)\b",
        r"\b(pivotal|crucial|key|significant|vital) (role|moment)\b",
        r"\bmark(s|ed|ing)? a (shift|turning point|milestone)\b", r"\breflects? (a )?broader\b",
        r"\bsetting the stage\b", r"\bindelible mark\b", r"\bdeeply rooted\b",
        r"\bevolving landscape\b", r"\bfocal point\b", r"\blasting (legacy|impact)\b",
        r"\bbroader (context|trend|movement|history|reflection)\b",
        r"\b(sparked|generated|prompted) (debate|discussion|reflection)\b",
    ],
    "promotional": [
        r"\bnestled\b", r"\bin the heart of\b", r"\brich (cultural )?(heritage|history|tradition)\b",
        r"\bgroundbreaking\b", r"\brenowned\b", r"\bcommitment to\b", r"\bnatural beauty\b",
        r"\bdiverse (array|range)\b", r"\bbreathtaking\b", r"\bseamless(ly)?\b",
        r"\bexemplif(y|ies)\b", r"\bprofound\b", r"\bstunning\b",
    ],
    "coverage talk": [
        r"\bindependent coverage\b", r"\b(featured|profiled|cited) in\b",
        r"\b(national|regional|local|prominent|major) (media|news) (outlets|coverage)\b",
        r"\btrade publications\b", r"\bsocial media presence\b", r"\bdigital presence\b",
    ],
    "vague attribution": [
        r"\bexperts (say|argue|note|believe)\b", r"\bobservers (have )?(noted|cited)\b",
        r"\bcritics (argue|say|note)\b", r"\bindustry reports\b", r"\bdescribed in scholarship\b",
        r"\bresearchers (have )?(noted|highlighted|suggested)\b", r"\bseveral (sources|publications)\b",
    ],
    "vague connection": [
        r"\bin connection (with|to)\b", r"\b(is|was|became|been) associated with\b",
        r"\bconnected (with|to)\b",
    ],
    "copula avoidance": [
        r"\b(functions|operates) as an?\b", r"\bholds the distinction\b",
        r"\b(features|offers|boasts) (a|an|several|numerous)\b",
    ],
    "negative parallelism": [
        r"\bnot only\b.*\bbut\b", r"\b(is|are|was|isn['’]t|aren['’]t|wasn['’]t)( not)? just\b.*[—–,;]\s*(it|they|but)\b",
        r"\bit['’]s not\b.*[—–,;]\s*it['’]s\b", r"\brather than\b",
    ],
    "formula / filler": [
        r"\bdespite (its|these|their|the) .*challenges\b", r"\bfaces (several|numerous|many) challenges\b",
        r"\bfuture (outlook|prospects|directions)\b", r"^\s*(in summary|in conclusion|overall)\b",
        r"\bit['’]?s? (is )?(important|crucial|worth) (to )?(note|noting|remember|consider)\b",
        r"\bnot (widely|extensively|publicly) (documented|available|disclosed)\b",
        r"\bbased on (the )?available information\b", r"\blow profile\b",
        r"\bas of my (last|latest) (knowledge|training)\b",
    ],
    "chat leftovers": [
        r"\bI hope this helps\b", r"^\s*(certainly|of course|sure)[!,]", r"\bwould you like me to\b",
        r"\blet me know if\b", r"\bhere is (a|an|the|your)\b", r"\byou['’]re absolutely right\b",
    ],
    "placeholders / artifacts": [
        r"\[(your name|insert[^\]]*|describe[^\]]*|link to[^\]]*)\]", r"\b20\d\d-(xx|XX)-(xx|XX)\b",
        r"INSERT_|PASTE_[A-Z_]+", r"oaicite|oai_citation|contentReference", r"turn\d+(search|image|news|file)\d+",
        r"\[cite:\s*\d", r"start_span|end_span", r"grok[-_]card|grok_render", r"【\d+†",
        r"\[(attached_file|web):\d+\]", r":::writing", r"utm_source=(chatgpt|openai|copilot)",
        r"referrer=grok", r"↩",
    ],
}

COMPILED = {c: [re.compile(p, re.IGNORECASE) for p in ps] for c, ps in CATEGORIES.items()}
HEADING = re.compile(r"^\s*(#{1,6}|={1,6})\s*(.+?)\s*=*\s*$")
SMALL = {"a", "an", "the", "and", "or", "of", "in", "on", "for", "to", "by", "with", "at", "from", "vs"}


def title_case(heading):
    words = [w for w in re.findall(r"[A-Za-z][\w'’-]*", heading)]
    if len(words) < 3:
        return False
    rest = [w for w in words[1:] if w.lower() not in SMALL]
    return len(rest) >= 2 and all(w[0].isupper() for w in rest)


def main():
    text = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else sys.stdin.read()
    lines = text.splitlines()
    words = max(1, len(re.findall(r"\b\w+\b", text)))
    hits = defaultdict(list)

    for n, line in enumerate(lines, 1):
        for cat, pats in COMPILED.items():
            found = sorted({m.group(0) for p in pats for m in p.finditer(line)})
            if found:
                hits[cat].append((n, found, line.strip()))
        h = HEADING.match(line)
        if h and title_case(h.group(2)):
            hits["title-case heading"].append((n, [h.group(2)], line.strip()))
        if re.match(r"^\s*[-*•–]\s*\*\*[^*]+\*\*\s*:?", line):
            hits["inline-header list item"].append((n, ["**Label:**"], line.strip()))
        if re.search(r"[\U0001F300-\U0001FAFF\u2600-\u27BF]", line) and re.match(r"^\s*([-*#•]|\S{1,2}\s)", line):
            hits["emoji formatting"].append((n, ["emoji"], line.strip()))

    em = text.count("—")
    bold = len(re.findall(r"\*\*[^*]+\*\*|'''[^']+'''", text))
    triplets = len(re.findall(r"\b\w+, \w+(?: \w+)?,? and \w+", text))

    if not hits:
        print("No pattern flags.")
    for cat, items in hits.items():
        print(f"\n## {cat} ({len(items)})")
        for n, found, line in items:
            snippet = line if len(line) <= 110 else line[:107] + "..."
            print(f"  L{n}: [{', '.join(found)}] {snippet}")

    per_k = lambda x: x * 1000 / words
    print("\n## density")
    print(f"  words: {words}")
    print(f"  em dashes: {em} ({per_k(em):.1f}/1000 words){'  <- high' if per_k(em) > 5 else ''}")
    print(f"  bold spans: {bold} ({per_k(bold):.1f}/1000 words){'  <- high' if per_k(bold) > 5 else ''}")
    print(f"  'x, y, and z' lists: {triplets} ({per_k(triplets):.1f}/1000 words){'  <- check for reflexive triplets' if per_k(triplets) > 8 else ''}")
    vocab = len(hits.get("overused vocabulary", []))
    print(f"  lines with overused vocabulary: {vocab}{'  <- clustered' if per_k(vocab) > 10 else ''}")
    print("\nFlags are prompts to reread. Fix by adding specifics or cutting, not by swapping synonyms.")


if __name__ == "__main__":
    main()
