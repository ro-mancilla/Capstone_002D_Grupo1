# Pattern reference

Detailed notes and before/after rewrites for each tell in SKILL.md. Examples are illustrative rewrites in the spirit of those in Wikipedia's "Signs of AI writing" guide.

## Contents

1. Inflated significance and legacy
2. Talking about coverage instead of content
3. Tacked-on "-ing" analysis
4. Promotional language
5. Vague attributions and overgeneralization
6. Challenges / future-prospects formula
7. List titles treated as things
8. Overused vocabulary (with rough timeline)
9. Avoiding plain copulas
10. Vague connection words
11. Negative parallelisms
12. Rule of three
13. Elegant variation
14. Formatting tells
15. Chat leftovers, placeholders, and tool artifacts
16. Citation problems
17. Edit summaries and short comments
18. Things that are NOT reliable tells

---

## 1. Inflated significance and legacy

**Watch for:** stands/serves as, is a testament/reminder, a pivotal/crucial/key moment, marks a shift, reflects broader trends, setting the stage for, indelible mark, deeply rooted, evolving landscape, focal point, enduring legacy, "sparked debate", "raises questions about".

Chatbots attach significance to mundane facts, even etymology or census numbers, sometimes after conceding the subject is minor ("Though it saw only limited use, it contributes to the broader history of..."). For plants and animals they tend to stress ecosystem roles and conservation efforts that may not exist.

> Before: The institute was established in 1989, marking a pivotal moment in the evolution of regional statistics and reflecting a broader movement toward decentralization.
>
> After: The institute was established in 1989 under a Catalan law that gave the region its own statistical service, separate from Spain's national institute.

If you can't name the concrete consequence, drop the significance claim.

## 2. Talking about coverage instead of content

**Watch for:** independent coverage, featured/profiled in, national/regional media outlets, trade publications, "has been covered by", "maintains an active social media presence".

This pattern lists *where* a subject was mentioned and what kind of outlet it was, rather than *what* was reported. It often echoes notability-guideline wording, as if arguing for the subject's importance inside the text itself.

> Before: She has been featured in Wired, Vogue, and other prominent media outlets, and maintains an active social media presence.
>
> After: A 2023 Wired profile described her work advising two provincial governments on AI procurement.

## 3. Tacked-on "-ing" analysis

**Watch for:** sentence-final clauses with highlighting, underscoring, emphasizing, reflecting, symbolizing, ensuring, contributing to, fostering, cultivating, enhancing, showcasing; also "valuable insights", "resonates with", "aligns with".

> Before: The station has eight tracks and six platforms, serving as a major hub and contributing to the socio-economic development of the region.
>
> After: The station has eight tracks and six platforms.

When a source actually makes the interpretive claim, attribute it concretely and check that the source says it. Tools with web search often pin these interpretations on named sources that never made them.

## 4. Promotional language

**Watch for:** boasts, vibrant, rich (figurative), profound, nestled, in the heart of, groundbreaking, renowned, showcasing, exemplifies, commitment to, natural beauty, diverse array, seamless, value-driven, thoughtfully designed.

Models slide into travel-brochure or press-release voice even when asked for neutral text, and even when told to "remove promotional tone". Newer models are subtler than older ones: fewer "the best", more quiet praise.

> Before: Nestled in the breathtaking hills, the town boasts a vibrant cultural heritage and offers visitors a fascinating glimpse into the region's diverse tapestry.
>
> After: The town lies at 1,500 m in the hills south of Gondar. Its weekly market draws traders from surrounding districts.

For products: describe what a feature is and does. "The doors open with buttons instead of handles" rather than "discreet buttons replace traditional handles, preserving the vehicle's clean and modern profile."

## 5. Vague attributions and overgeneralization

**Watch for:** experts argue, observers have noted, critics say, researchers and conservationists, described in scholarship, industry publications such as, several sources (when there are one or two).

> Before: Scholars describe the confederacy as a fluid coalition, and modern researchers treat it as a cultural formation rather than a state.
>
> After: Historian Jane Doe (2011) describes the confederacy as a shifting coalition of Benue valley groups rather than a fixed state.

Don't write "such as" before a list that is exhaustive, and don't turn one reviewer into "reviewers".

## 6. Challenges / future-prospects formula

The rigid arc: "Despite its [praise], X faces several challenges, including... Despite these challenges, X continues to [thrive/play a vital role]." Often followed by a speculative "Future outlook" section. Mentioning real problems is fine; the formula is the tell.

> Before: Despite its success, the canal faces challenges, including drought. Future investments in technology could enhance its efficiency and maintain its relevance.
>
> After: Drought in 2023 lowered Gatun Lake enough that the authority cut daily transits from 36 to 22 for several months.

## 7. List titles treated as things

For list articles or descriptive titles, avoid openings like "'List of songs about Mexico' is a curated compilation of..." or "'Catchment area (health)' refers to...". Start with the subject itself: "A catchment area is the geographic area from which a hospital draws its patients."

## 8. Overused vocabulary

Documented as over-represented in model output since late 2022: additionally (sentence-initial), align with, boasts, bolstered, crucial, deep dive, delve, emphasizing, enduring, enhance, fostering, garner, highlight (verb), interplay, intricate/intricacies, key (adj.), landscape (abstract), meticulous, pivotal, robust, showcase, tapestry (abstract), testament, underscore (verb), valuable, vibrant.

Rough eras (not hard cutoffs):
- 2023 to mid-2024: additionally, boasts, crucial, delve, intricate, meticulous, pivotal, tapestry, testament, vibrant.
- Mid-2024 to mid-2025: align with, enhance, fostering, highlighting, showcasing, underscore.
- Mid-2025 on: emphasizing, enhance, highlighting, showcasing, plus the coverage/notability phrasing in section 2.

Grok-style output also leans on pseudo-scientific words (causal, empirical, correlate).

Take this literally: the specific words are the tell, not their synonyms. The fix is a sentence built on a fact, not "underscores" → "stresses".

## 9. Avoiding plain copulas

**Watch for:** serves as, stands as, functions as, operates as, represents, marks, boasts, features, offers, maintains, "holds the distinction of being", "ventured into politics as a candidate", "began his career as".

> Before: Gallery 825 serves as the association's exhibition space. The gallery features four separate spaces.
>
> After: Gallery 825 is the association's exhibition space. It has four rooms.

AI copyedits reliably make this change in the wrong direction, so check revisions for it.

## 10. Vague connection words

**Watch for:** associated with, in connection with, connected to, particularly/widely associated.

> Before: He became associated with musical education in Knokke-Heist.
>
> After: He taught violin and chamber music at the Knokke-Heist music academy from 1976.

## 11. Negative parallelisms

Forms: "not only X but also Y"; "it's not just X, it's Y"; "X isn't A — it's B"; "not A, not B, just C"; "Y rather than X". Also the slow version across two sentences ("He came from a famous family. His life, however, took a different path").

They imply the reader held a misconception nobody mentioned. Use them only when you're correcting an actual, stated misunderstanding.

> Before: This isn't just a sourcing issue — it's a framing issue.
>
> After: The sources are fine; the disagreement is about how the article frames them.

## 12. Rule of three

Reflexive triplets ("tiles, metals, and plastics"; "clarity, flow, and readability"; "unity, togetherness, and faith") make thin analysis look thorough. Use the real number of items: one, two, or five. The tell is strongest in places where nobody normally bothers with rhetorical rhythm, such as edit summaries.

## 13. Elegant variation

Some models penalize repetition, producing chains like "the constraints of socialist realism... Soviet artistic constraints... state-imposed artistic norms... the confines of the regime". Pick the clearest term and repeat it. (Some non-native writers are taught to avoid repetition, so this alone proves little.)

## 14. Formatting tells

- **Em dashes** used where a comma, colon, or parentheses would do, especially for punchy emphasis, often with spaces around them. As of mid-2026, Claude was the one major model found to use them more than professional writers.
- **Bold** scattered across body text, "key takeaway" style.
- **Inline-header lists:** `- **Label:** sentence` for content that is really prose. Sometimes with no colon at all, or with pseudo-bullets (•, –, emoji).
- **Title Case Headings** instead of sentence case.
- **Title repeated as a top heading**; headings that contain only subheadings; skipped heading levels; top-level (`=`/`#`) headings throughout.
- **Horizontal rules** between every section.
- **Tiny tables** that should be a sentence or an infobox (a two-row "Name / Designation" table).
- **Emoji** on headings or bullets.
- **Boilerplate sections:** "Awards and recognition", "Legacy", "Challenges", "Future prospects", "Conclusion", "X and Y" headings generally.
- **Markdown in non-Markdown destinations:** `**bold**`, `## Heading`, `[text](url)`, ```` ```wikitext ```` fences pasted into a wiki or CMS.

## 15. Chat leftovers, placeholders, and tool artifacts

Remove before delivering text for reuse:
- Addressed to the requester: "Certainly!", "Here is a...", "I hope this helps", "Would you like me to...", "Let me know if...", advice about how to submit or format the text, notes asserting the text follows some style guide.
- Cutoff and gap talk: "as of my last update", "not widely documented", "is limited in the provided sources", "based on available information", "I've inferred", "maintains a low profile".
- Placeholders: `[Your Name]`, `[Describe the section]`, `(Add URL here)`, `INSERT_SOURCE_URL`, `2025-XX-XX`, `<!-- Add if available -->`.
- Tool residue: `:contentReference[oaicite:0]`, `oai_citation`, `turn0search0`, `Source+2`, `{"attribution":...}`, `[cite: 3]`, `[span_1](start_span)`, `grok-card`, `【12†L4-L9】`, `[attached_file:1]`, `[web:1]`, `:::writing{...}`, `↩` back-links, `utm_source=chatgpt.com` / `openai` / `copilot.com`, `referrer=grok.com`.

When writing yourself: if you need the user to fill something in, say so in your chat reply, not inside the deliverable, unless they asked for a template.

## 16. Citation problems

Never fabricate. Common failures to avoid:
- Links that were never live; DOIs that resolve to unrelated papers; ISBNs with bad checksums.
- Book citations with no page number, or page numbers that don't contain the claim.
- Real, general-topic sources attached to claims they don't support.
- Access dates that predate the edit, or that are placeholders.
- References defined in a list but never used inline; reused references with broken syntax.
- Hallucinated category or template names.

If you can't verify a source, say so and leave the claim out or mark it as needing a citation.

## 17. Edit summaries and short comments

AI edit summaries follow templates:
- Canned compliance: "improved clarity, neutrality, and flow in line with Wikipedia's guidelines".
- Procedural narration: "while preserving the original meaning", "retained references", "avoided promotional language".
- Emphasis on sourcing in the abstract: "added sourced content with independent secondary sources".
- Parameter-level detail: "corrected infobox parameters (image_size)".
- Stating the obvious: "addressed reviewer feedback".

A human-style summary names the content change briefly: "add 2019 album and label; trim duplicate tour list". Common abbreviations (ce, rm, +ref) are fine.

For discussion comments, avoid: sectioned walls of text with headings, restating the other person's points back to them, "I understand your concerns about...", reassurance that the content "reflects my own thoughts", closing "In summary" paragraphs, and invented policy shortcuts.

## 18. Things that are NOT reliable tells

Don't "fix" these; they are normal in human writing:
- Perfect grammar or a professional register.
- Mixed casual and formal tone.
- Formal or academic-sounding vocabulary in general (only the specific words in section 8 are over-represented).
- Occasional transition words.
- Curly quotes (word processors produce them; Claude doesn't typically emit them anyway).
- Correct, complex markup.
- Unsourced content by itself.

Degrading good text to look human makes it worse. Specific, accurate, plainly stated text is the target.
