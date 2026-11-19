---
name: "ui-writing-english"
description: "Write, review or translate into English the UI labels and microcopy of mobile/web apps (buttons, dialogs, errors, forms, toasts, empty states), US default with UK notes."
---

# English UI writing (labels and microcopy)

Use this skill whenever you write, edit, review or translate *into English* any text that appears inside a mobile or web product: buttons, menus, tabs, titles, dialogs, alerts, form labels, hints, placeholders, errors, toasts, notifications, empty states, onboarding, settings, links and accessible labels.

The skill covers **content and language only** — word choice, grammar, tone, capitalization, punctuation, numbers and regional conventions. It deliberately ignores string-file formats and i18n code.

Default variety: **US English**. See §13 for UK/Commonwealth differences.

The rules are synthesized from Apple HIG and the Apple Style Guide, Microsoft Writing Style Guide (and the Windows UX guidelines), Google developer documentation style guide and Material Design, Shopify Polaris, Atlassian Design System, GitHub Primer, IBM Carbon, GOV.UK Design System and style guide, Mailchimp Content Style Guide, Nielsen Norman Group, W3C/WCAG and Digital.gov plain-language guidance. Where these sources disagree, the disagreement is stated and a default is recommended — don't present a contested choice as a universal rule.

---

## 1. Workflow

1. **Identify the platform and the house style.** Is it iOS/macOS (Apple conventions), Android/Material, Windows, or a web product with its own design system? If the product has a style guide or glossary, it wins over this skill. If not, apply the defaults below and state them.
2. **Identify the component** (button, dialog title, error, toast…). Each has its own rules (§4–§9).
3. **Identify the moment and the reader's emotional state.** Voice stays constant; tone adapts. A payment failure and a completed workout call for different tones (Apple, Mailchimp).
4. **Draft, then cut.** Remove every word that doesn't carry meaning. Read it aloud (Apple).
5. **Check against the checklist** in §15.
6. When reviewing, show ❌ → ✅ rewrites and name the rule behind each change.

---

## 2. Core principles

- **Clarity beats cleverness.** "It's always more important to be clear than entertaining" (Mailchimp). "Just saying 'Send' often works better than 'Let's do it!'" (Apple).
- **Be brief, but not cryptic.** Lead with what matters.
  - ❌ "If you want to add visual effects or alterations to your image, select filters."
  - ✅ "Select filters to add effects to your image." (Microsoft)
- **Plain language.** Everyday words over jargon: *use* not *utilize*, *set up* not *configure*, *help* not *facilitate*, *after* not *once* (Google, GOV.UK, Microsoft). Aim for short sentences (≤25 words is a common ceiling) and a general-audience reading level (roughly grade 8 is a widely used rule of thumb, not a formal standard).
- **Active voice, present tense.**
  - ❌ "The changes will be applied when the app is restarted."
  - ✅ "Restart the app to see your changes." (Microsoft)
  - Exception: use passive or subjectless phrasing to avoid blaming the user in errors — ❌ "You entered an incorrect password" → ✅ "Incorrect password" (Windows UX).
- **Talk to the user as "you".** Omit "you can" when the sentence works without it (Microsoft).
- **Don't say "easy", "simple", "just", "quick".** What's easy for you may not be for the user (Google, Primer).
  - ❌ "Just fill out this easy questionnaire" → ✅ "Complete this 3-question survey to get started" (Polaris)
- **Be consistent.** Build a term list and use one word for one concept everywhere (Apple). If the flow starts with "Get started", continues with "Continue" and ends with "Done", keep that pattern throughout.
- **No humor where people are stressed.** No jokes, "oops", "uh-oh" or "whoops" in errors, waiting states or failures (Apple, NN/g, GOV.UK, Primer). Light delight is fine in rare, positive moments.

### Person: "you", "my", "we"

- **Don't mix perspectives** in the same phrase or screen. ❌ "Change your settings in My Account" (Material, Apple).
- **Omit the possessive when meaning is clear:** "Favorites", "Profile", "Settings" rather than "Your Favorites" (Apple).
- **"My"/"I"** is acceptable when the user is the one speaking or asserting control: checkbox and toggle labels such as "Remember me", "I agree to the Terms of Service" (Microsoft). In navigation, pick either "Your…" or "My…" and use it everywhere.
- **"We"** is contested:
  - Apple and Google/Material avoid it ("Unable to load content" instead of "We're having trouble loading this content").
  - Microsoft (Windows apps), Atlassian and Mailchimp use it for warmth and to take responsibility ("We couldn't save your file").
  - **Default:** follow the platform (Apple → avoid; otherwise either is fine). Pick one and never use "we" in a sentence that blames the user.

---

## 3. Capitalization

### The main split

| Convention | Who uses it |
|---|---|
| **Sentence case** for everything (titles, buttons, menus, tabs, labels) | Microsoft, Google/Material 3, Polaris, Atlassian, Primer, IBM Carbon, GOV.UK — the large majority |
| **Title case** for buttons, menu items, alert titles (fragments), notification titles | Apple platforms (HIG). Apple also lets you choose a style per element type as long as it's consistent |

**Default:** sentence case, except in native Apple-platform UI where you follow Apple's title-case conventions for buttons, menus and alert titles.

- Sentence case: ✅ "Create purchase order" · ❌ "Create Purchase Order" (Polaris)
- Apple title case: ✅ "Add to Cart", "Save As…", "Turn On"

**Title-case rules (when you need them):** capitalize the first and last word and all nouns, pronouns, verbs, adjectives and adverbs. Lowercase articles (*a, an, the*), coordinating conjunctions (*and, but, or, nor, for, yet, so*) and short prepositions (Microsoft: four letters or fewer — *on, to, in, of, for, with, from*) unless they're first or last. Apple always capitalizes the last word ("Turn On", "Log Out Of").

### Other capitalization rules

- **When in doubt, don't capitalize** (Microsoft). ❌ "Limited-Time Offer" → ✅ "Limited-time offer".
- **Proper nouns only:** capitalize real product and brand names ("Shopify Payments", "Apple Pay"), not generic features ("analytics", "order entry", "themes") (Polaris, Microsoft). Clarity over creative feature names.
- **Respect brand casing:** iPhone, GitHub, Mailchimp, YouTube.
- **Lowercase common nouns:** internet, website, online, email, app (Mailchimp).
- **Don't capitalize spelled-out acronyms** unless they're proper nouns: "application programming interface (API)" (Microsoft).
- **After a numeral**, no capital: "2 references", not "2 References" (Primer).
- **After a colon in a heading**, capitalize the first word (Microsoft, Google).
- **No ALL CAPS for emphasis.** It reads as shouting and reduces legibility beyond single glanceable words (Microsoft, NN/g, GOV.UK). If the design calls for uppercase, keep the source text in normal case and apply caps visually; some screen readers spell all-caps strings letter by letter.
- **No all-lowercase as a style** either (Microsoft).

---

## 4. Buttons and calls to action

### Rules

1. **Start with a verb** that says exactly what happens. "When labeling buttons and links, it's almost always best to use a verb" (Apple). Buttons are "action-led" (Polaris).
2. **Add the object when the verb alone is ambiguous:** Add → Add items → Add selected items (Windows UX). ✅ "Create order", "Buy shipping label", "Add menu item" vs ❌ "New order", "Buy", "Add a menu item" (Polaris).
3. **Drop articles** in labels: "View settings", not "View the settings" (Apple, Polaris).
4. **Keep it short:** 1–3 words typically (Apple, Atlassian, Carbon). No line breaks (Primer).
5. **No end punctuation** on buttons (Windows UX, Primer). Exception: an ellipsis on commands that need more input (§11.2).
6. **Sentence case** (or Apple title case, §3).
7. **Accepted verbless labels:** OK, Cancel, Done, Next, Back, Previous, Settings, Options, Details, More, Less, New (Windows UX, Apple).
8. **Avoid generic "OK", "Yes/No", "Submit"** when a specific verb exists.
   - Apple: use "OK" only in purely informational alerts, and don't use Yes/No. For confirmations, name the action: "Erase", "Convert", "Clear", "Delete".
   - NN/g: "Delete file / Keep file", not "Yes / No".
   - Prefer "Send", "Pay", "Confirm and send", "Save and continue" over "Submit" (GOV.UK).
   - The older Windows UX guide still allows Yes/No for confirmations that need thought. Modern guidance (Apple, NN/g, Polaris, Atlassian) prefers verbs. **Default: verbs.**
9. **Cancel means cancel.** Always title the button that backs out of an alert's action "Cancel" (Apple). Don't rename it unless it's unclear what's being canceled (Windows UX). Never make Cancel the default action for a destructive dialog.
10. **"Now" only to distinguish** an immediate action from a deferred one: "Download" (opens a page) vs "Download now" (starts it) (Windows UX). Don't use "later" if nothing will happen later: ❌ "Restart later" → ✅ "Don't restart" / "Cancel".
11. **Don't be cute:** ❌ "Let's do it!", "Yay, sign me up!" → ✅ "Send", "Sign up" (Apple).
12. **Match the trigger, the dialog and the confirm button** (same verb and noun): menu item "Delete project…" → title "Delete project?" → button "Delete project".

### Destructive actions

- Name the destruction precisely: *Delete* (it's gone), *Remove* (it leaves this context but still exists), *Discard* (unsaved changes), *Erase*, *Clear* (empty fields or selections) (IBM Carbon).
- Always include a safe way out (Cancel) (Apple).
- For critical, irreversible actions, state the consequence and consider an extra step (e.g., typing the name) — and prefer offering Undo where possible (NN/g).

### Standard flow buttons

- Start: "Get started" / "Start now" · Proceed: "Continue" (GOV.UK) or "Next" — pick one · Finish: "Done" · Save and move on: "Save and continue" · Add more: "Add another [thing]" · Sign out: "Sign out".

---

## 5. Dialogs, alerts and confirmations

### Title

- **Describe the situation or ask the specific question.** Never "Error", "Warning" or "Error 329347 occurred" as the whole title (Apple).
- **Use {verb} + {noun}:** ✅ "Delete customer?", "Discard unsaved changes?", "Edit email address" · ❌ "Are you sure you want to delete customer?", "Discard?" (Polaris).
- **Avoid "Are you sure…?"** State the consequence instead. ❌ "Are you sure you want to change your currency?" → ✅ "Changing store currency will affect reporting, pricing, and more" (Polaris). Include names and counts: "Delete 3 files?" "Delete 'Q3 report'?" (NN/g).
- **Phrase positively where you can:** "Turn on file sharing?" rather than "Turn off file sharing?" when either framing works (Windows UX).
- **Length:** aim for one line; never more than two (Apple).
- **Punctuation:** a question gets "?"; a complete-sentence title gets a period (Apple); a fragment gets none.

### Body

- Only include it if it adds value. Use complete sentences with sentence case and ending punctuation (Apple).
- Put the most important information first; explain consequences: ✅ "This can't be undone."
- Don't explain what the buttons do (Apple).
- Express requirements with "need": ✅ "To buy a shipping label, you need to enter the total weight of your shipment" · ❌ "You must enter…" (Polaris).
- Tone: direct and neutral, not oblique, not accusatory, not hiding the severity (Apple).

### Buttons

- **The buttons answer the title** ("call and response", Windows). "Save changes?" → **Save** / **Don't save** / **Cancel**. Don't answer a question with OK/Cancel.
- Prefer verbs related to the text: "View all", "Reply", "Ignore" (Apple).
- Button order follows the platform (Apple/macOS: confirm on the right; Windows: confirm first). Don't fight it in copy.

**Worked example**

- ❌ Title "Warning" · Body "Are you sure you want to do this?" · Buttons "Yes" / "No"
- ✅ Title "Delete 'Invoice 2026-09'?" · Body "You can't undo this action." · Buttons "Cancel" / "Delete"

---

## 6. Error messages

### Structure: what happened → why (if useful) → how to fix

- ✅ "Can't rename 'my house' because a file with that name already exists." (Windows UX)
- ✅ "Choose a password with at least 8 characters." instead of ❌ "That password is too short" (Apple — say how to succeed).
- ✅ "Use only letters for your name." instead of ❌ "Don't use numbers or symbols." (Apple — positive instruction).
- ✅ "To save this product, make 2 changes: Enter title, Add weight" · ❌ "There are 2 errors on this page. Invalid title, Weight must be…" (Polaris).

### Field-level validation (GOV.UK patterns, widely copied)

- Missing text → "Enter [thing]": ✅ "Enter your full name"
- Missing choice → "Select [thing]": ✅ "Select your country"
- Wrong format → describe the right format with an example: ✅ "Enter an email address in the correct format, like name@example.com"
- Too long/short → state the limit: ✅ "Name must be 35 characters or less"
- Use the same words as the field label, and the same text in the summary and inline message.

### Words to avoid in errors

| Avoid | Why / use instead |
|---|---|
| invalid, illegal, forbidden, prohibited, bad | Accusatory or jargon. Describe what's needed (GOV.UK, Polaris, Windows UX, NN/g). Note: Windows suggests "incorrect", NN/g considers even that blame-laden — prefer describing the fix |
| error, failure, fatal, catastrophic | Windows UX: "problem", "unable to", "serious" |
| abort, kill, terminate | "stop", "cancel", "end" |
| An error occurred / Unknown error / Something went wrong (alone) | Say what failed and what to do. If you truly don't know, at least give a next step: "Something went wrong. Try again in a few minutes." |
| oops, uh-oh, whoops | Insincere (Apple, GOV.UK) |
| Error codes as the message | Put codes in secondary "details" text, never as the only explanation (NN/g, Windows) |
| Exclamation marks | Never in errors (Mailchimp, Material, Primer) |
| "You failed to…", "You didn't…" | Blame. Use passive or neutral phrasing |

### "Please" and "sorry" (sources disagree)

- GOV.UK and Atlassian: don't use either ("please" implies the step is optional; "sorry" makes errors seem worse and doesn't help).
- Microsoft: use "please" only when asking for something inconvenient or when the app is at fault; use "sorry" only for serious problems (data loss, can't continue, must contact support, service outage).
- Google: "please" in instructions is overdoing it.
- **Default:** use neither in routine copy. Consider "sorry" for serious failures the product caused, once. ✅ "Sorry, we can't process payments right now. Your card wasn't charged."

### Delivery and tone

- Put the message next to the problem (Apple, NN/g). Keep the user's input. Don't show errors before the user has had a chance to finish.
- Don't put errors that need action in toasts that auto-dismiss (NN/g, Carbon).
- Don't rely on red color alone — the text must carry the meaning (NN/g, WCAG).
- Match severity to the tone: serious problems get plain, calm, direct language; don't mask them.

---

## 7. Forms: labels, hints, placeholders, required fields

- **Visible labels always.** Placeholder text is not a label: it disappears, strains memory, is often low-contrast and isn't reliably read by assistive tech (NN/g, GOV.UK, Primer). Apple is softer (placeholders like "Email" are acceptable with a label recommended) — **default: always a label; use placeholders only for format examples, never for essential information.**
- **Labels:** short nouns in sentence case, no colon: ✅ "Email address", "First name", "Share with" · ❌ "Share with:" (GOV.UK, Material, Carbon). ≤3 words where possible (Primer).
- **Hint text:** one short sentence, no link, no long explanation (GOV.UK hints omit the full stop; other systems use one for full sentences — pick one). ✅ "For example, 27 3 2007" · ✅ "We'll only use this to send order updates."
- **Questions as page headings** work well in step-by-step flows: "What is your date of birth?" (GOV.UK: one question per page). Use a question mark only when it is a real question: ✅ "Forgot your password?" · ❌ "Reset password?" as a link (Polaris).
- **Required vs optional (sources disagree):** GOV.UK marks optional fields with "(optional)" and never uses asterisks; NN/g and Primer mark required fields (asterisk is widely understood). **Default:** mark the minority. If most fields are required, add "(optional)" to the rest. If you use an asterisk, explain it once ("Required fields are marked with *") and make sure it has an accessible name.
- **Toggles and checkboxes:** describe what happens when it's on; people infer the opposite (Apple). ✅ "Show previews". Prefer "turn on / turn off" over "enable / disable" (Polaris avoids "disable" for its disability connotation; Microsoft uses "turn on/off" for toggles).
- **Microcopy explaining why you ask** builds trust: "We'll use your phone number to confirm your identity."

---

## 8. Empty states, onboarding, tooltips

### Empty states (NN/g, Apple, Polaris)

An empty screen has three jobs: say what's going on, teach what goes here, and offer a way forward.

- First use: ✅ "No projects yet" + "Create a project to start tracking work." + button "Create project"
- No results: ✅ "No results for 'blue sofa'" + "Check the spelling or try a broader search."
- All done: ✅ "You're all caught up"
- Titles should be action-oriented: ✅ "Create orders and send invoices" · ❌ "Orders and invoices" (Polaris). Subtitles are conversational and can keep articles.
- Don't show "No data" while data is still loading (NN/g).

### Onboarding

- Fast, optional and focused on value, not on teaching the OS (Apple). Prefer contextual tips at the moment of need over long up-front tours.
- Ask for permissions with the benefit first: ✅ "Turn on notifications to know when your order ships." [Turn on] [Not now]. "Not now" is kinder than "No" for a soft decline.

### Tooltips

- Only if they add something the label doesn't; users shouldn't need a tooltip to complete a task (NN/g). Icon-only buttons need a real text label or at least a descriptive tooltip ("Search by image").
- Periods: Windows puts periods on full-sentence tooltips; Material omitted them on single sentences. **Default:** fragments no period; full sentences with a period.

---

## 9. Toasts, success messages, notifications, loading

### Toasts / snackbars

- One short line about the operation just performed (Material). Noun + past participle is the compact pattern: ✅ "Product updated", "Link copied", "Message sent" · ❌ "Your product has been successfully updated!"
- At most one action; that action shouldn't be "Dismiss" or "Cancel" (Material). "Undo" is the classic.
- Not for errors that need action (NN/g).

### Success messages

- Say what happened and, if useful, what's next. Avoid "Success!" and "successfully" — the message itself signals success (Atlassian). ❌ "Success! Your file was successfully uploaded." → ✅ "File uploaded".
- Exclamation marks: at most one per screen, for genuinely exciting moments (Polaris, Carbon): ✅ "You've launched your first store!" · ❌ "You've updated your product title!". Atlassian avoids them entirely.

### Push notifications (Apple HIG)

- Title: short and specific, no end punctuation (title case on Apple). Skip generic titles like "New document".
- Body: complete sentences, sentence case, proper punctuation; don't truncate on purpose.
- Don't include the app name (the system shows it) or sensitive content in previews.
- Lead with the key information: ✅ "Your order is out for delivery. It should arrive by 6 PM."

### Loading and progress

- Under ~1 s: nothing. 2–10 s: an indicator with a short label. Longer: percent or steps and a time estimate (NN/g).
- Be specific: ✅ "Uploading 3 photos…", "Updating address 3 of 50" · ❌ "Please wait…"
- Use the ellipsis character "…" with no space before it: "Loading…", "Saving…" (Material, Apple, Atlassian).

---

## 10. Links and accessibility

- **Descriptive link text.** It must make sense out of context (WCAG 2.4.4, Google). Never "click here" or "here" (Apple, Primer, Mailchimp). "Learn more" alone is "the new click here" (NN/g) — make it specific: ✅ "Learn more about pricing", "See shipping rates".
- **Accessible names for icon-only buttons** are short and don't include the control type: "Save", not "Save button" (Apple accessibilityLabel). Add context when the visible label is ambiguous: visible "Edit" under "Shipping address" → accessible name "Edit shipping address" (Polaris).
- **Label in name:** the accessible name must contain the visible text, ideally at the start (WCAG 2.5.3). Visible "Search" → name "Search products", not "Find items".
- **Don't rely on sensory characteristics** (position, color, shape, sound) alone (WCAG 1.3.3). ❌ "Select from the options in the right sidebar" → ✅ "Select from these options" (Mailchimp). Avoid "above/below"; use "preceding/following" or name the element (Google). Directional language also breaks in right-to-left layouts.
- **Alt text:** functional images describe the action ("Open chat with support"); informative images get a brief description; decorative images get empty alt (W3C decision tree).
- **Device verbs:** in UI strings you rarely need the verb at all ("Save your changes", not "Tap Save to save your changes"). In instructions:
  - Microsoft: device-agnostic *select* (buttons, links, items), *choose* (options), *enter* (typing), *turn on/off* (toggles), *clear* (uncheck).
  - Apple/Google: device-accurate — *tap* on touch, *click* on pointer devices. Apple: *choose* a menu command, *select* an item.
  - **Default:** *select* for cross-platform help text; *tap/click* only when the device is known. Never *hit* (Google).

---

## 11. Mechanics

### 11.1 Periods

- **No period:** buttons, labels, tabs, menu items, headings, titles, checkbox/radio labels, fragments, single-phrase toasts, list items of a few words.
- **Period:** complete sentences in body text, dialogs, errors, helper text, notifications (Windows, Apple).
- Material and Polaris omit the period on a single sentence standing alone; Apple and Windows keep it. **Default:** full sentence → period; fragment → none; always use periods when there's more than one sentence or a link follows.

### 11.2 Ellipsis on commands (sources differ)

- Apple and Windows desktop: add "…" to a command that needs more input before it completes: "Save As…", "Print…", "Rename…". Don't add it to commands that simply open a window (About, Settings, Properties) (Windows UX). Apple's button guidance applies it more broadly (opening another window or view).
- Web and Android: generally not used. **Follow the platform; be consistent.**
- In instructions, drop the ellipsis: "Select Save as" (Microsoft).

### 11.3 Contractions

- **Use them** — they sound human: *can't, don't, it's, you're, we'll* (Microsoft, Polaris, Atlassian, Mailchimp, Carbon).
- Don't contract a noun with a verb ("Your file's ready" → "Your file is ready"). Avoid awkward ones (*there'd, it'll, would've*). Don't mix "can't" and "cannot" in the same product (Microsoft).
- **Exception:** GOV.UK avoids negative contractions (*cannot, do not*) for readability. Follow it for UK public-sector services.

### 11.4 Other punctuation

- **Ampersand:** write "and" (Polaris, GOV.UK, Primer, Atlassian — "&" also localizes poorly). Only in brand names or tight legacy labels.
- **Serial (Oxford) comma:** use it in US tech copy ("Android, iOS, and Windows") (Microsoft, Polaris, Atlassian, Mailchimp, Chicago). AP and most UK styles omit it.
- **Exclamation points:** rare; never in errors or warnings; never more than one (§9).
- **Question marks:** only for real questions.
- **Semicolons:** avoid in UI; split into two sentences (Primer, Polaris).
- **Colons:** not after labels (GOV.UK, Material). OK to introduce a list in body text.
- **Dashes:** em dash without spaces in US style ("Your files—photos and videos—are safe") (Microsoft, Mailchimp); en dash for ranges (Mon–Fri, 9–5) or better, "to" in prose ("10 November to 21 December", GOV.UK; "rows 1 to 4", Atlassian). Hyphenate compound modifiers: "14-day trial", "high-risk order" (Polaris).
- **Quotes and apostrophes:** use curly quotes and apostrophes. US: double quotes, commas and periods inside. Use quotes around user-generated object names in messages: Can't rename "my house"… (Windows UX). Don't quote UI element names — bold them in docs, or just name them.
- **Abbreviations:** avoid *e.g., i.e., etc.* in UI — write "for example", "such as", "that is", "and more" (Google, Atlassian, GOV.UK, Mailchimp). Spell out uncommon acronyms at first use; don't invent new ones (Polaris).
- **Slashes:** avoid "and/or" and "file(s)"; write for the actual count (see §12.2).

### 11.5 Numbers

- **Use numerals in UI**, even for 1–9: ✅ "You have 3 messages" · ❌ "three messages" (Material, Polaris, Atlassian). (Microsoft's prose rule of spelling out zero–nine doesn't apply to UI.)
- Don't start a sentence with a numeral in body text; rephrase ("More than 10 apps…").
- Thousands separator: comma from 4 digits (1,024; $12,000) (Microsoft, Polaris). Don't abbreviate like "12 k" in body text; "12K" is acceptable in compact counters if consistent.
- Units: space between number and unit, no plural on abbreviations: ✅ "3.4 lb", "2 kg", "15 MB" · ❌ "3.4lb", "4 lbs" (Polaris).
- Percent: "%" in UI ("50%"); Microsoft uses "percent" in prose.
- Ranges: "5–10 minutes" or "5 to 10 minutes".

### 11.6 Dates and times (US default)

- Spell out or abbreviate the month to avoid DD/MM vs MM/DD ambiguity: ✅ "December 11, 2026", "Dec 11, 2026", "Thursday, Dec 11" · ❌ "12/11/26" (Polaris, Google). No ordinals: ❌ "January 23rd".
- Time: 12-hour. Pick one AM/PM style and keep it: "10:45 AM" (Microsoft) or "10:45 am"/"7 pm" (Polaris, Mailchimp). Use "noon" and "midnight" instead of "12 PM/12 AM" (Microsoft, GOV.UK).
- Include a time zone for anything scheduled that people in different regions might see (Mailchimp).
- Relative time for recent events: "Just now", "5 min ago", "Yesterday", then absolute dates.

### 11.7 Currency

- Symbol before the amount, no space: "$20", "$19.99". Drop ".00" when all prices on the screen are whole (GOV.UK style: "£75"). When the currency is ambiguous, add the code after the amount: "$10,000 USD" · ❌ "USD$10,000" (Polaris).

---

## 12. Inclusive and localization-ready English

### 12.1 Inclusive language

- **Gender-neutral:** use "you", plurals or singular *they*; never *he/she* or *s/he* (Microsoft, Polaris, Mailchimp). ✅ "Enter their email address". Neutral nouns: chair, humanity, workforce, sales representative, mail carrier, synthetic.
- **Replace loaded metaphors:** blocklist/allowlist (not blacklist/whitelist); primary/replica, main, source (never master/slave); legacy/exempt (not grandfathered); ethical/unethical hacker (not white/black hat) (Google, Polaris, Microsoft).
- **Disability:** don't use *crazy, insane, blind to, crippled, lame, dumb* figuratively; avoid pity language (*suffers from*). Use *placeholder* not *dummy* and *check* not *sanity check* (Google). Prefer *turn off*, *inactive* to *disabled* for settings (Polaris). People-first wording is the US default ("people with low vision"); GOV.UK uses identity-first "disabled people" — follow the market.
- **Culture:** no idioms, sports metaphors, holidays or seasons as universal references ("August isn't summer in the southern hemisphere") (Google). Use diverse example names.
- **Don't guilt or pressure** (confirm-shaming): ❌ "No thanks, I don't like saving money" → ✅ "No thanks".

### 12.2 Writing source strings that translate well

English is usually the source language for localization, so write it to survive translation:

- **Full sentences, not assembled fragments.** Word order, gender and plural rules differ by language; "Delete" + " " + "{item}" breaks in Spanish and Japanese.
- **One string per meaning.** "New" on a folder and "New" on a message may need different translations (gender agreement). "Share", "Post", "Clear", "Empty" can be verbs, nouns or adjectives — make the part of speech obvious ("Share file", "Send an email") and give translators context.
- **Keep small grammatical words in sentences:** "that", "the", "to" help translators (Mailchimp, Google). (Articles are dropped in *labels*, not in sentences.)
- **Avoid ambiguous words:** *once* (→ after), *since* (→ because), *while* (→ although) (Google).
- **Write plural-aware text** instead of "file(s)": "1 file", "3 files", or rephrase ("Files deleted").
- **Leave room for expansion.** Translations of short strings can be 2–3× longer (W3C/IBM table: up to 10 characters +200–300%; 11–20 +180–200%; 21–30 +160–180%; 31–50 +140–160%; over 70 about +130%). "Views" → Italian "Visualizzazioni". Don't design a label that only fits in English.
- **No "&", "e.g.", idioms, puns or wordplay** in strings that will be localized.

---

## 13. US vs UK (and Commonwealth) English

Most US design systems mandate US English (Atlassian, Polaris). For UK products (and largely Ireland, Australia, New Zealand, South Africa, India), apply these differences. Canada mixes: British *-our* and *-re*, American *-ize* and vocabulary.

### Spelling

| US | UK | Note |
|---|---|---|
| color, favorite, behavior, honor | colour, favourite, behaviour, honour | |
| center, meter, theater | centre, metre, theatre | |
| organize, customize, authorize | organise, customise, authorise (GOV.UK, Australia) — *-ize* is also correct "Oxford" British | Pick one for the product |
| analyze, catalog, dialog | analyse, catalogue, dialogue | "dialog box" is common in UK computing |
| canceled, labeled, traveling, modeling | cancelled, labelled, travelling, modelling | Both use "cancellation" |
| enroll, fulfill | enrol, fulfil | |
| license (n./v.), practice (n./v.) | licence (n.) / license (v.), practice (n.) / practise (v.) | "Driving licence" |
| program | programme (TV, events) but **program** (software) | |
| gray, check (bank), defense | grey, cheque, defence | |
| judgment | judgement (judgment in law) | |

### Vocabulary

| US | UK |
|---|---|
| cell phone | mobile (phone) |
| ZIP code | postcode |
| state | county (often optional) |
| last name | surname (both understood) |
| cart | basket (e-commerce) |
| fill out (a form) | fill in (GOV.UK) |
| vacation | holiday |
| apartment | flat |
| Mom | Mum |
| check (restaurant) | bill |
| sales tax | VAT (a different concept — localize, don't translate) |
| toll-free | freephone |

### Grammar and punctuation

- Collective nouns: UK allows "the team are"; US "the team is". Keep company and product names singular in UI ("Acme is updating…") so it works for both.
- US *gotten*; UK *got*. UK allows *learnt, spelt, dreamt*.
- Quotes: UK traditionally uses single quotes with logical punctuation; GOV.UK uses single quotes for UI names and unusual terms, double only for speech.
- Titles: "Mr", "Dr" without periods in UK.
- Serial comma: usually omitted in UK style.
- Dashes: UK commonly uses a spaced en dash ( – ) where US uses a closed em dash (—).
- Abbreviations: GOV.UK writes "eg", "ie", "etc" without periods (and prefers to avoid them).
- GOV.UK public-service tone: no negative contractions (*cannot*), no "please"/"sorry", "Continue" not "Next", "(optional)" on optional fields.

### Dates, times, numbers

- Date order: UK "24 January 2026" (no comma, no ordinal; GOV.UK "4 June 2017"); numeric DD/MM/YYYY. Spell the month when a date may be read by both audiences.
- Time: UK uses the 24-hour clock widely ("17:30"); GOV.UK prose uses "5:30pm". Use "midday"/"midnight".
- Weeks start on Monday in the UK and much of Europe; Sunday in the US.
- Currency: "£10", "€10" in UK/Ireland (symbol before). Number separators are the same as US (1,000.50).
- Units: metric officially (with miles and pints in some contexts).

---

## 14. Glossary of commonly confused UI terms

| Term | Use | Source |
|---|---|---|
| **Add / Create / New / Insert** | Add = put an existing thing into a context; Create = make from scratch; New = start creating (short label); Insert = place at a position | Carbon |
| **Remove / Delete / Clear** | Remove = take out, object survives; Delete = destroy; Clear = empty fields/selections or uncheck | Carbon, Microsoft |
| **Edit / Update** | Edit = enter change mode; Update = commit edits | Carbon |
| **Save / Apply** | Save = store changes; Apply = save without closing the dialog | Carbon |
| **Cancel / Close / Done** | Cancel = abandon the action; Close = dismiss a window; Done = finished | Carbon, Apple |
| **Reset / Restore** | Reset = back to defaults/last saved; Restore = bring back after deletion | Carbon |
| **Settings / Preferences / Options** | "Settings" on modern iOS, Android, Windows, macOS 13+ | Platforms |
| **Continue / Next** | Pick one per product (GOV.UK: Continue) | GOV.UK, Apple |
| **Sign in / Sign out / Sign up** | Two-word verbs; *sign-in*, *signup* as nouns/adjectives (Google). Preferred over *log in* by Microsoft, Google, GOV.UK, Primer | |
| **Log in / login** | Only if the product already uses it: *log in* (verb), *login* (noun). Never "login to" | Microsoft, Google |
| **Set up / setup** | *set up* verb, *setup* noun/adjective | Microsoft, Google |
| **Email** | "email", not "e-mail". As a verb: Microsoft yes, Google no — "send an email" works everywhere | |
| **Username, checkbox, dropdown, pop-up, Wi-Fi, OK** | These spellings (not "user name", "check box", "Ok", "okay") | Google, Apple |
| **Select / Choose** | Select items and UI elements; choose among preferences or menu commands | Microsoft, Apple |
| **Turn on/off vs enable/disable** | Prefer turn on/off | Microsoft, Polaris |
| **Unavailable / dimmed** | Not "grayed out" or "disabled" in user-facing text | Google, Apple |
| **Stop responding** | Not "hang" or "crash" | Microsoft |
| **Learn more** | Never alone: "Learn more about…" | NN/g |
| **Sign in with / Continue with** | "Continue with Google" is common for social sign-in buttons | Observed |

---

## 15. Review checklist

- [ ] Platform and house style identified; one capitalization scheme applied consistently.
- [ ] Every button starts with a specific verb (or is a standard verbless label), has no end punctuation, and matches the dialog title's verb.
- [ ] No "OK/Yes/No" on confirmations; Cancel is labeled "Cancel".
- [ ] Dialog titles name the action and object; no "Are you sure…?"; consequences stated.
- [ ] Errors say what happened and how to fix it; no blame, no "invalid/illegal", no jokes, no exclamation marks, no bare error codes.
- [ ] "Please"/"sorry" only where justified; "we" used consistently (or not at all on Apple).
- [ ] Labels are visible and colon-free; placeholders only show examples; required/optional marked consistently.
- [ ] Toasts are short past-participle statements; no "successfully".
- [ ] Link text is descriptive; icon buttons have accessible names that contain the visible label.
- [ ] No reliance on color, position or shape in instructions.
- [ ] Contractions used naturally; numerals for numbers; unambiguous dates; time zones where needed.
- [ ] Inclusive terms; no idioms, "&", "e.g.", "file(s)" or concatenated fragments in strings that will be translated.
- [ ] US or UK spelling applied consistently throughout.

---

## 16. Sources

- Apple Human Interface Guidelines — Writing, Alerts, Buttons, Menus, Notifications, Text fields, Loading, Onboarding, VoiceOver: https://developer.apple.com/design/human-interface-guidelines/writing
- Apple Style Guide: https://support.apple.com/guide/applestyleguide/welcome/web
- Microsoft Writing Style Guide: https://learn.microsoft.com/style-guide/welcome/ (capitalization, person, contractions, please, sorry, bias-free communication, UI interactions)
- Microsoft Windows app writing style: https://learn.microsoft.com/windows/apps/design/style/writing-style
- Windows UX guidelines (errors, command buttons, confirmations): https://learn.microsoft.com/windows/win32/uxguide/mess-error
- Google developer documentation style guide: https://developers.google.com/style (UI elements, word list, global audience, accessibility)
- Material Design writing guidance: https://m3.material.io/foundations/content-design/overview ; https://m1.material.io/style/writing.html
- Shopify Polaris content: https://polaris-react.shopify.com/content/grammar-and-mechanics
- Atlassian Design System content: https://atlassian.design/foundations/content
- GitHub Primer content: https://primer.style/product/getting-started/foundations/content/
- IBM Carbon content guidelines: https://carbondesignsystem.com/guidelines/content/action-labels/
- GOV.UK Design System and A–Z style guide: https://design-system.service.gov.uk/ ; https://guidance.publishing.service.gov.uk/writing-to-gov-uk-standards/style-guides/a-to-z-style-guide/
- Mailchimp Content Style Guide: https://styleguide.mailchimp.com/
- Nielsen Norman Group: error-message guidelines, confirmation dialogs, OK–Cancel, link labels, empty states, placeholders, required fields, tooltips, progress indicators — https://www.nngroup.com/
- W3C: Text size in translation https://www.w3.org/International/articles/article-text-size ; WCAG 2.2 Understanding 1.3.3, 2.4.4, 2.5.3
- Digital.gov plain language: https://digital.gov/guides/plain-language/