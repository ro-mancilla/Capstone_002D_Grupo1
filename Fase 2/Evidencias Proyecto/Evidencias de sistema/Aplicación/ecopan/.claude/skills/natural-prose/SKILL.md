---
name: natural-prose
description: Write and revise prose so it reads like careful human writing instead of generic chatbot output. Use this skill whenever drafting or editing text meant to be read by other people (articles, encyclopedia or wiki entries, bios, company or product descriptions, blog posts, reports, essays, emails, talk-page or forum comments, edit summaries, cover letters), and especially when the user asks to "make this sound less like AI", "humanize", "remove the ChatGPT tone", "sound more natural", "de-fluff", or complains that text feels generic, promotional, or robotic. Also use it to review someone else's text for AI-style tells.
---

# Natural Prose

This skill is distilled from Wikipedia's field guide "Signs of AI writing", which catalogs the patterns editors see in chatbot-generated text. It turns that list of symptoms into guidance for writing well.

## The one idea behind everything

Language models predict the most statistically likely continuation, so their output drifts toward the average. Rare, specific facts get replaced by generic, positive, important-sounding claims that could describe almost anything. "Inventor of the first train-coupling device" becomes "a revolutionary titan of industry." The subject gets less specific and more inflated at the same time.

Nearly every tell below is a symptom of that drift. So the fix is almost never swapping a word for a synonym. The fix is to put back the specific thing the generic phrase was standing in for, or to delete the phrase if there is nothing specific behind it.

Treat this as a writing-quality goal. Stripping surface tells from text that is still vague, unsourced, or invented just hides the problem. If a sentence can't be made specific because you don't actually know the fact, cut it or say plainly that you don't know.

## Workflow

1. **Collect the concrete material first.** Dates, numbers, names, what actually happened, who said what, where the claim comes from. Good prose is built from these. If the material is thin, the text should be short.
2. **Draft plainly.** Write what the thing is and what it did. Prefer "is", "has", "was", "wrote", "used".
3. **Run the revision pass** below. For a longer piece, run `scripts/scan_tells.py` on the draft for a quick flag list (see "Scanner" below), then read the flagged lines yourself.
4. **Read it once as a skeptical reader.** For every sentence ask: what fact does this sentence add? If the answer is "it tells me the subject matters", rewrite or delete.

## Revision pass

Work through these in order. Details and before/after examples for each are in `references/patterns.md`; read it when revising anything longer than a couple of paragraphs or when asked to review someone else's text.

### Content

- **Inflated significance.** Cut sentences whose only job is to say something is pivotal, a testament, a turning point, part of a broader trend, an enduring legacy, or deeply rooted. If it really was a turning point, show the before and after with facts.
- **Tacked-on analysis.** Watch for sentences ending in an "-ing" clause that interprets the fact: "..., highlighting its importance", "..., reflecting the region's rich heritage", "..., contributing to economic growth". Usually delete the clause. If the interpretation is real, attribute it to a named source that actually says it.
- **Promotional tone.** No "boasts", "vibrant", "nestled", "in the heart of", "rich heritage", "showcasing", "commitment to", "diverse array", "breathtaking", "seamless". Describe features neutrally, as a reference work would.
- **Talking about coverage instead of content.** Don't write "has been featured in national media and trade publications" or "maintains an active social media presence." Say what the source reported.
- **Vague authorities.** No "experts say", "observers note", "researchers have highlighted", "industry reports suggest" without naming who. Don't imply many sources when you have one, and don't write "such as" before a list that is actually complete.
- **Formula endings.** Avoid the "Despite its X, Y faces several challenges... Despite these challenges, Y continues to thrive" arc, and speculative "Future outlook" wrap-ups. Avoid "In summary / In conclusion / Overall" paragraphs that restate what was just said.
- **Filler disclaimers.** No "it's important to note", "details are not widely documented", "keeps a low profile", "based on available information". If information is missing, either leave the topic out or state the gap once, plainly, without guessing what the missing facts "likely" are.

### Language

- **Overused vocabulary.** Be suspicious of dense clusters of: additionally (to open a sentence), align with, boasts, bolstered, crucial, delve, emphasizing, enduring, enhance, fostering, garner, highlight (verb), interplay, intricate, key (adjective), landscape (abstract), meticulous, pivotal, robust, showcase, tapestry, testament, underscore (verb), valuable, vibrant. One is fine. Several in a paragraph is a tell. Replacing them with near-synonyms doesn't help; rewrite the sentence around the fact.
- **Avoiding "is" and "has".** "Serves as", "stands as", "functions as", "represents", "features", "offers", "boasts" often just mean "is" or "has". Use the plain verb.
- **Vague connection words.** "Associated with", "in connection with", "connected to" often hide a relationship you can state directly: "was CEO of", "taught at", "founded".
- **Negative parallelisms.** Limit "not just X, but Y", "it's not X, it's Y", "no X, no Y, just Z", and "Y rather than X". They manufacture a contrast nobody raised.
- **Reflexive triplets.** Don't default to lists of three adjectives or three phrases. Use as many items as there really are.
- **Elegant variation.** Don't cycle through synonyms to avoid repeating a noun ("the artist... the painter... the creative visionary"). Repeating the right word is clearer.
- **Stiff substitutes.** Prefer wrote, moved, used, tried, died over authored, relocated, utilized, attempted, passed away.

### Style and formatting

- **Em dashes.** Use sparingly. Current Claude models overuse them relative to professional writers. Often a comma, colon, parentheses, or a full stop is better. Never use them for dramatic "punch".
- **Bold.** Don't bold key phrases throughout body text. Bold is for a term at the point it's defined, if at all.
- **Inline-header bullet lists.** Avoid the "- **Label:** explanation" pattern for material that reads fine as prose. Use lists only for genuinely list-like content.
- **Headings.** Use sentence case ("Early life and career"), not Title Case. Don't add a heading repeating the document title, empty parent headings, horizontal rules between every section, or boilerplate sections like "Awards and recognition", "Challenges", "Legacy", "Future prospects" unless the material genuinely warrants them.
- **Tables and emoji.** No tiny two-column tables that should be a sentence. No emoji as bullets or heading decorations.
- **Match the target format.** If the destination uses wikitext, HTML, or plain text, don't leave Markdown syntax (`**`, `##`, backtick fences) in it.

### Leftovers from the chat

Before handing over text meant to be pasted elsewhere, remove anything addressed to the requester rather than the reader: "Here is a draft...", "I hope this helps", "Would you like me to...", notes about what the text complies with, placeholders like `[Your Name]` or `2025-XX-XX`, and tool artifacts (`oaicite`, `turn0search0`, `[cite: 3]`, `utm_source=chatgpt.com`). Never invent citations, URLs, DOIs, ISBNs, or page numbers; leave a clearly marked gap instead and tell the user.

## What human writing tends to have

Plain "is/has/there is" sentences. Simple verbs. Definite claims where they're earned ("was the first", "is the only"), with a source. Occasional hedges and intensifiers ("perhaps", "very", "tends to"). Unremarkable connective phrasing ("as a result of", "in order to", "the fact that"). Uneven paragraph lengths driven by how much there is to say. Specific, sometimes odd details that no template would produce.

## What not to do

Don't add typos, bad grammar, slang, or random casualness to seem human. Polished grammar and formal register are not AI tells, and deliberately degrading text makes it worse. Don't pad with detail you can't verify. The goal is text that is specific, accurate, and neutral, not text engineered to fool a detector.

## Short texts

- **Emails and comments:** say the point in the first sentence, skip "I hope this message finds you well", don't break a short message into bolded sections, don't close with a summary paragraph or a stack of offers.
- **Edit summaries and changelogs:** name what changed ("Added 1998 founding date from company filing; removed duplicate awards list"). Don't narrate compliance ("improved neutrality, clarity and flow while preserving meaning"), don't list what you left untouched, and don't enumerate template parameters.

## Scanner

`scripts/scan_tells.py` flags candidate tells with line numbers and a density summary:

```bash
python scripts/scan_tells.py draft.md          # or pipe text via stdin
```

It is a prompt for rereading, not a verdict. A clean scan does not mean the text is good, and a flagged word is sometimes the right word (e.g. "underscore" meaning the character, "key" meaning a physical key). Fix flagged lines by making them more specific, not by swapping synonyms.
