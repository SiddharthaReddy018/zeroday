#!/usr/bin/env python3
"""Single source of truth for test counts (ADR-010 / R9): builds the test table from Surefire XML.

Usage:
  mvn test
  python3 tools/test_report_counts.py                      # prints Markdown
  python3 tools/test_report_counts.py --inject docs/TEST_REPORT.md   # replaces the block between
      <!-- BEGIN GENERATED:tests --> and <!-- END GENERATED:tests -->
Never type test counts into documents by hand.
"""
import glob, sys, xml.etree.ElementTree as ET
from collections import OrderedDict

def collect(pattern="target/surefire-reports/*.xml"):
    rows = OrderedDict()
    for path in sorted(glob.glob(pattern)):
        for case in ET.parse(path).getroot().iter("testcase"):
            cls = case.get("classname", "?")
            r = rows.setdefault(cls, [0, 0, 0, 0])
            r[0] += 1
            if case.find("skipped") is not None: r[3] += 1
            elif case.find("failure") is not None or case.find("error") is not None: r[2] += 1
            else: r[1] += 1
    return rows

def render(rows):
    t = [sum(r[i] for r in rows.values()) for i in range(4)]
    out = ["| Metric | Value |", "|---|---|",
           "| Total tests | %d |" % t[0], "| Passed | %d |" % t[1],
           "| Failed/Errors | %d |" % t[2], "| Skipped | %d |" % t[3],
           "| Test classes | %d |" % len(rows), "", "| Test class | Tests | Pass | Fail | Skip |", "|---|---|---|---|---|"]
    for c, r in rows.items():
        out.append("| `%s` | %d | %d | %d | %d |" % (c, r[0], r[1], r[2], r[3]))
    return "\n".join(out)

def main():
    rows = collect()
    if not rows:
        sys.exit("no surefire XML found under target/surefire-reports (run mvn test first)")
    md = render(rows)
    if "--inject" in sys.argv:
        f = sys.argv[sys.argv.index("--inject") + 1]
        s = open(f).read()
        a, b = "<!-- BEGIN GENERATED:tests -->", "<!-- END GENERATED:tests -->"
        if a not in s or b not in s:
            sys.exit("markers not found in " + f)
        s = s[:s.index(a) + len(a)] + "\n" + md + "\n" + s[s.index(b):]
        open(f, "w").write(s)
        print("injected into", f)
    else:
        print(md)

if __name__ == "__main__":
    main()
