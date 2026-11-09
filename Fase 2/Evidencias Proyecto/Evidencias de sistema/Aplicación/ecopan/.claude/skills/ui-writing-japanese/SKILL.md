---
name: "ui-writing-japanese"
description: "Write, review or translate into Japanese the UI labels and microcopy of mobile/web apps (buttons, dialogs, errors, forms, toasts): register, kana/kanji, katakana, width, punctuation."
---

# Japanese UI writing (labels and microcopy)

Use this skill whenever you write, edit, review or translate *into Japanese* any text shown inside a mobile or web product: buttons, menus, tabs, titles, dialogs, form labels, help text, errors, toasts, notifications, empty states, onboarding, settings and links.

It covers **content and language only** — register and politeness, verb forms, kanji/kana choice, katakana loanwords, character width and spacing, punctuation, names, dates and numbers. It ignores string-file formats, encoding and i18n code.

Rules are synthesized from: SmartHR Design System writing guidelines (the most detailed public Japanese UI writing guide), JTF日本語標準スタイルガイド（翻訳用）第3.0版 (Japan Translation Federation), the Microsoft Japanese Localization Style Guide and Microsoft's 2008 長音 policy change, Apple Human Interface Guidelines (Japanese edition), 文化審議会「公用文作成の考え方」(2022), 内閣告示「外来語の表記」(1991), 文化審議会「敬語の指針」(2007), デジタル庁デザインシステム, GovTech東京「入力フォームガイダンス」, Ubie Vitals UXライティング, Goodpatch, Mercari and wordrabbit UX-writing articles, and 財務省 guidance on tax-inclusive price display.

**Japanese sources disagree on several mechanical points** (spacing between scripts, the final ー, symbol widths, button forms). This skill lists each disagreement and recommends a default. **Consistency within the product matters more than which option you pick** — every source says so.

---

## 1. Workflow

1. **Check for a house style or platform.** If the product has a 表記ルール / 用字用語集, it wins. If you're building for Apple, Microsoft or Google ecosystems, match their OS-level terms (see §4.1, §6.2). Otherwise use the defaults here and state them once.
2. **Decide register** (B2B neutral vs consumer warm; §2) and the product's policies for the choices in §13.
3. **Identify the component.** Labels, titles and buttons use nouns or plain forms; sentences use です・ます (§2.1).
4. **Write natively, don't translate word by word.** Drop pronouns, avoid passive calques, don't translate "successfully", "Oops", "Are you sure" literally (§11).
5. **Check notation**: kana vs kanji (§5), katakana (§4), width and spacing (§6), punctuation (§7).
6. Run the checklist in §14. When reviewing, give ❌ → ✅ rewrites and name the rule.

---

## 2. Register and politeness

### 2.1 Which form where

The consistent rule across JTF, SmartHR, Microsoft and 公用文: **sentences are です・ます; labels, headings, buttons and short list items are nouns (体言止め) or plain forms.** Never mix です・ます and だ・である within running text.

| Element | Form | ✅ | ❌ |
|---|---|---|---|
| Button | verbal noun or dictionary-form verb | 保存 · 閉じる · メンバーを招待 | 保存します · 閉じてください |
| Screen / dialog title | noun phrase | 権限の追加 · 通知設定 | 権限を追加する |
| Section heading | noun phrase | 支払い方法 | 支払い方法を設定しましょう。 |
| Help / description text | です・ます full sentence + 。 | 半角英数字で入力してください。 | 半角英数字で入力 |
| Error message | です・ます full sentence + 。 | メールアドレスを入力してください。 | メールアドレス未入力 |
| Toast / completion | past tense です・ます | 保存しました | 保存完了！ · 保存されました。 |
| Status label | noun | 承認待ち · 下書き · 送信済み | 承認を待っています |
| Log / history line | plain verb phrase | ユーザーを作成 | ユーザーを作成しました（SmartHR） |

### 2.2 Keep 敬語 light

- **Excessive keigo hides meaning** (SmartHR, 公用文). ✅ 確認してください · ❌ ご確認いただけますようお願いいたします.
- **Prefer できる over することができる**: ✅ 削除できます · ❌ 削除することができます.
- **Prefer 〜する over 〜を行う**: ✅ 設定してください · ❌ 設定を行ってください.
- **させていただく** is appropriate only when the action requires the other party's permission *and* the speaker benefits (敬語の指針). ❌ メンテナンスを実施させていただきます → ✅ メンテナンスを実施します。/ メンテナンスのため、サービスを停止します。
- **No 二重敬語**: ❌ おっしゃられる, ご覧になられる, お伺いさせていただく → ✅ おっしゃる, ご覧になる, 伺います.
- **No バイト敬語**: ❌ 〜のほうお願いします, よろしかったでしょうか, 〜になります (for "is") → ✅ 〜をお願いします, よろしいでしょうか, 〜です.
- **ございます**: 公用文 avoids it in public-facing explanations; Microsoft uses honorifics only in supplier-to-customer contexts. Default: です, and reserve ございます for very formal commerce or support moments.
- **Correct grammar in polite forms**: ❌ ご参考ください → ✅ 参考にしてください.

### 2.3 お／ご (美化語)

- Fixed expressions are normal in consumer and commerce UI: お知らせ, お問い合わせ, ご利用, お支払い, お届け先, ご注文.
- For instructions, B2B style uses the plain verb (確認してください); consumer/service style often uses ご確認ください. **Pick one per product.**
- **No お／ご on buttons** — a button is the user's own action and voice: ✅ 登録 · ❌ ご登録 (wordrabbit).
- **No お／ご on katakana words**: ❌ おパスワード, ごログイン.

### 2.4 Addressing the user

- **Omit the subject; don't use あなた** (Microsoft). ❌ あなたのパスワードを入力してください → ✅ パスワードを入力してください · ❌ あなたのアカウントは削除されました → ✅ アカウントを削除しました.
- When a reference is unavoidable: お客様 (commerce, support, banking), ご自身, or a role name (管理者, メンバー, 従業員).
- Honorifics after names: 様 in account and commerce screens (山田 太郎 様); さん in collaborative B2B (SmartHR: 須磨 英子さんの申請); none in data lists.

### 2.5 Tone by product type

- **B2B / productivity** (SmartHR, freee, Money Forward style): neutral, precise, です・ます, minimal お／ご, no ！, one term per concept.
- **Consumer** (Mercari, LINE, games): warmer. Softer requests — 〜できます, 〜しましょう, 〜してみましょう; occasional ！ in celebratory moments. Mercari avoids casual 〜だよ (trust) and avoids guilt or pressure (❌ まだやっていないんですか？); it frames progress (あと2ステップで完了します).
- **Health / government**: everyday words over technical ones (Ubie: ✅ 病院 ❌ 医療機関 · ✅ 病気 ❌ 疾患; 公用文: ✅ すぐに対応すべき重要な課題 ❌ 喫緊の課題).
- Apple: choose a voice that fits the product — a bank conveys trust, a game conveys excitement.

### 2.6 Softness scale for requests (direct → soft)

1. 〜してください。 — standard, clear, not rude. The default for instructions and errors.
2. 〜できます。 — offers a possibility (「設定からいつでも変更できます。」). Good for tips.
3. 〜しましょう。 — friendly, encouraging. Consumer onboarding.
4. お手数ですが、〜してください。 / 恐れ入りますが、〜 — acknowledges extra effort. Use sparingly.
5. 〜いただけますようお願いいたします — too heavy for UI.

Never use plain imperatives (❌ 入力しろ, ❌ 入力せよ).

---

## 3. Buttons and commands

### 3.1 Forms

All of these are standard:

- **Sino-Japanese verbal noun, する dropped** (most common): 保存, 削除, 送信, 編集, 追加, 検索, 登録, 作成, 共有, 更新, 変更, 適用, 複製, 印刷, 確定.
- **Dictionary-form native verb**: 閉じる, 戻る, 続ける, 始める, 開く, 取り込む, 元に戻す, やり直す.
- **Katakana loan**: キャンセル, ログイン, ログアウト, リセット, ダウンロード, スキップ, コピー.
- **Object + を + verb** when the object matters: メンバーを招待, ファイルを選択, 権限を追加.

Rules:

1. **Title vs button by particle** (SmartHR): title 「権限の追加」, button 「権限を追加」. Use を, not の, in buttons.
2. **Drop する** on verbal nouns by default (SmartHR, Goodpatch, Microsoft): ✅ 追加 · ❌ 追加する.
   - *Disagreement:* wordrabbit and some consumer apps keep する for emphasis or clarity (購入する, 予約する; 共有する when 共有 alone could read as a state). **Default: bare noun for utility commands; 〜する acceptable for prominent CTAs. Never mix both in one button group.**
3. **Never polite forms or 。 in buttons**: ❌ 保存します · ❌ 削除してください · ❌ ログインする。
4. **Keep it short**: one or two words (Apple). Two-kanji verbal nouns fit tight spaces.
5. **Device-neutral verbs in text**: SmartHR uses 押す (not クリック/タップ) and 開く for links; Microsoft uses 選択する. Don't say クリック on touch devices.

### 3.2 Dialog buttons

- **The cancel button is always 「キャンセル」** (Apple). Don't make it the default action for destructive dialogs.
- **「OK」 only for purely informational alerts.** For confirmations use the specific verb: 削除, 消去, 破棄, ログアウト (Apple). Never はい/いいえ for new app UI (Apple, Goodpatch) — Windows legacy dialogs still use them; don't copy that.
- **The "キャンセルのキャンセル" problem** (Goodpatch): ❌ 「編集をキャンセルしますか？」[キャンセル][OK] → ✅ 「編集内容を破棄しますか？」[キャンセル][破棄]. キャンセル should only ever mean "back out of this dialog".
- **Save prompt**: 「変更を保存しますか？」[保存しない][キャンセル][保存].
- Button verb = message verb: 「ログアウトしますか？」[キャンセル][ログアウト].

### 3.3 Precise action vocabulary (SmartHR)

- **追加** (default for creating an item) vs **登録** (registering information: 従業員情報, パスワード) vs **作成** (documents, applications).
- **キャンセル** (stop before execution) vs **取り消し** (undo something already executed) vs **中断** (stop midway).
- **削除** (delete permanently) vs **外す／除外** (take out of a group or list) vs **解除** (undo a setting or link: 連携を解除).
- **取り込む／インポート** (import) vs **書き出す／エクスポート** (export).
- **有効にする／無効にする** (switches), **選択する** (radio), **チェックを入れる／外す** (checkbox).
- **複製** (duplicate an object) vs **コピー** (copy text).
- **移動** (not 遷移), **再読み込み**, **並べ替え**.

---

## 4. Katakana loanwords

### 4.1 The final 長音 ー (a well-known disagreement)

- **内閣告示「外来語の表記」(1991):** English *-er, -or, -ar* are written with ー as a rule; omission by custom is allowed.
- **Old JIS Z 8301 rule** (dropped ー in words of 3+ morae: コンピュータ, サーバ, ユーザ) was abandoned in the 2019 revision, which now follows the 内閣告示.
- **Microsoft switched in 2008** (IE8/Windows 7 onward): コンピューター, プリンター, フォルダー, ブラウザー, ドライバー. Some *-y* words stay short in Microsoft usage: メモリ, カテゴリ, プロパティ, セキュリティ, ディレクトリ.
- **JTF:** don't omit the final ー (コンピューター, ユーザー, メモリー).
- **SmartHR:** ー for -er/-or/-ar (ユーザー, フィルター, マスター); none for *-y* words (セキュリティ, アクセシビリティ); exceptions ブラウザ, フォルダ.
- **Google, Ubie:** ユーザー. **Apple** still writes ユーザ, コンピュータ, プレースホルダ in Japanese UI and docs.

**Default for new products:** follow the 内閣告示 majority — ユーザー, サーバー, コンピューター, プリンター, フォルダー, ブラウザー, エラー, フィルター, パラメーター. For *-y* words, choose a policy (e.g., セキュリティ, プロパティ, カテゴリー/カテゴリ) and write it in the glossary. **Match platform terms for OS-level concepts** (on iOS, system strings say ユーザ). Omitted ー can also be misread by text-to-speech (メモリ → 目盛り).

### 4.2 Separators in compounds

- 内閣告示 leaves this to each field; JTF says choose one of ・, a half-width space, or nothing, and stay consistent.
- Microsoft mirrors English spacing with a half-width space (ダイアログ ボックス, コントロール パネル, タスク マネージャー).
- Most Japanese web services use no separator (メールアドレス, プライバシーポリシー, ドラッグアンドドロップ).
- **Default:** no separator for established compounds; use ・ only where a long compound would be misread, and for foreign personal names (ドナルド・キーン). Never ＆ inside a compound (SmartHR: ドラッグアンドドロップ).

### 4.3 Spelling variants

- ウィ／ウェ／ウォ over ウイ／ウエ／ウオ (SmartHR, 公用文 第2表): ✅ ウィンドウ, ソフトウェア, ウェブ.
- バ／ビ／ブ over ヴ in general UI (内閣告示 allows ヴ; general usage prefers バ): バイオリン, ビデオ. Brand names keep official spelling.
- Common misspelling: ✅ シミュレーション ❌ シュミレーション.

### 4.4 Don't overuse katakana

- SmartHR: don't use katakana casually; users interpret it inconsistently. ✅ 改善 ❌ カイゼン · ✅ 自由記述 ❌ フリーテキスト · ✅ サンプル ❌ ダミー (negative nuance) · ✅ 動作環境 ❌ 推奨環境.
- 公用文: paraphrase unfamiliar loans (アジェンダ → 議題, サプライヤー → 仕入先) or add an explanation in （）.
- **Established loans are correct and often clearer**: ログイン, パスワード, アカウント (never 口座 — that's a bank account), ダウンロード, メール, アプリ, ファイル, プロフィール, リンク, ホーム.
- In-app data uses 更新 (情報を更新); OS updates are アップデート.
- Formal register: パソコン (not PC), スマートフォン (スマホ is fine in casual consumer copy).

### 4.5 Latin script

- Brand, product and format names stay in Latin script: iPhone, Google アカウント, Excel, PDF, CSV, Wi‑Fi, Bluetooth. Write 「CSVファイル」, not 「.csvファイル」 (SmartHR).
- Don't leave generic English words in Japanese text: ❌ Settingsを開く → ✅ 設定を開く.

---

## 5. Kanji vs kana

### 5.1 Principles

- Base notation on the 常用漢字表 (2010). Avoid non-jōyō kanji in UI unless the term is established; add readings to hard kanji if needed (Ubie: 膿（うみ）).
- **Hiragana for 形式名詞, 補助動詞, 補助形容詞 and most conjunctions/adverbs; kanji for content words** (公用文, SmartHR).
- Aim for readable density — roughly 30% kanji is a widely quoted heuristic (Ubie: 漢字3：ひらがな7), not a formal standard.

### 5.2 Word list

| ✅ Write | ❌ Avoid | Note |
|---|---|---|
| こと · もの · とき · ところ · ため | 事 · 物 · 時 · 所 · 為 | 形式名詞. Keep 時 for clock time (18時) |
| 〜してください | 〜して下さい | 補助動詞. 本動詞 keeps kanji (資料を下さい), rare in UI |
| 〜していただく · いたします | 頂く · 致します | 補助動詞 |
| できる | 出来る | |
| ある · ない | 有る · 無い | |
| など | 等 | 等 only when read とう |
| すべて · すでに · さまざま | 全て · 既に · 様々 | SmartHR. 公用文 本則 keeps 全て, 既に — *disagreement*; pick one |
| および · または · ならびに · もしくは | 及び · 又は · 並びに · 若しくは | Kanji is legal-document style; kana allowed in 広報 and UI |
| 〜ごとに · 次のとおり · あわせて · はじめて | 毎に · 通り · 併せて · 初めて | |
| わかる | 分かる | 公用文 uses 分かる; わかる allowed in 広報. Choose one |
| 元に戻す | もとに戻す | 元 = previous state; もと = basis (〜をもとに) |
| か所 · か月 | 箇所 · ヶ月 · カ月 | SmartHR |

### 5.3 送り仮名

- Follow the 内閣告示「送り仮名の付け方」 本則: ✅ 行う ❌ 行なう.
- In running text include okurigana: 申し込み, 取り消し, 書き込み, 引き落とし (SmartHR). Established compound nouns on forms may omit them (申込書, 振込先), as banks and government do.

---

## 6. Width and spacing

### 6.1 Character width

- **Full-width**: kana, kanji, Japanese punctuation 、。「」（）・…. **Never half-width katakana** (ｶﾀｶﾅ) (JTF, SmartHR, Microsoft).
- **Half-width**: Latin letters and Arabic digits (JTF, SmartHR, 公用文).
- **Avoid circled numbers** (①②) except when mirroring official documents (SmartHR).

### 6.2 Space between Japanese and Latin/digits (disagreement)

| Source | Rule | Example |
|---|---|---|
| JTF, SmartHR, current Apple | **No space** | PDFファイル · 2026年 · iPhoneユーザガイド |
| Microsoft, Google Help | **Half-width space** around Latin words and numbers (not next to Japanese punctuation or brackets) | Word を使用するときは、 · Google アカウント |

**Default:** no space (modern Japanese product writing). Use the Microsoft/Google convention only when localizing within those ecosystems. **Never use full-width spaces as padding.**

### 6.3 Symbols (disagreements noted)

- **Parentheses**: full-width （） with no surrounding spaces is the Japanese-native default (JTF, SmartHR). Microsoft uses half-width ( ) for access keys and supplements. Weekday in dates is often half-width: 9月23日(水).
- **Colon**: avoid in running text (JTF). For labels and examples, full-width 「例：山田 太郎」 (SmartHR). Microsoft uses half-width ":" after labels.
- **？ ！**: full-width when single, with a full-width space if text follows (SmartHR, JTF). Microsoft and Apple's Japanese HIG use half-width "?". Pick one.
- **Percent**: 50% (Microsoft, most apps) or 50％ (JTF). Pick one.
- **Ranges**: a wave dash, never a hyphen or half-width "~": 10〜20件 · 9:00〜18:00 · 2026/09/01〜2026/09/30. SmartHR uses 〜, JTF uses ～ — both render as a wave dash.
- **No spaces around full-width symbols** ・：／、。（）「」［］【】… (SmartHR).

---

## 7. Punctuation

### 7.1 、 and 。

- Use 、。 (JTF). 公用文 2022 made 、 the default for horizontal text too (「，」 is only an option).
- **No 。** in buttons, tabs, menu items, headings, labels, table headers, single-word list items, placeholders and example text.
- **Use 。** in complete sentences: dialog bodies, help text, errors, sentence-form list items. SmartHR puts 。 on tooltips even with a single sentence.
- **Toasts vary** (「保存しました」 without 。 is very common; SmartHR adds 。). Pick one.
- ・ for interchangeable parallel items (青・黄・赤); 、 when order matters (SmartHR).

### 7.2 ！ and ？

- **B2B / formal**: avoid ！ (JTF, Ubie: ❌ ありがとうございます！ ✅ ありがとうございます。). Microsoft removes "!" that would sound aggressive (警告! → 警告).
- **Consumer**: ！ is acceptable in celebratory or welcome moments (登録が完了しました！ · ようこそ！) but **never in errors or warnings**.
- ？ only in genuine questions (変更を保存しますか？).

### 7.3 Brackets and quotes

- 「」 for quotation, on-screen messages, file names and user input; 『』 for quotes within quotes.
- **Naming UI elements in text (disagreement)**: ［ ］ (SmartHR, Microsoft: ［保存］を押してください) vs 「 」 (Ubie, Apple, Google: 「次へ」を選んでください). Pick one.
- （） for supplementary notes. 【】 for user-named objects or badges (【必須】).
- Ellipsis: 「…」 in UI (読み込み中…). Traditional prose uses 「……」.
- ※ for notes.

---

## 8. Error messages

### 8.1 Structure

Three elements (SmartHR): **事象** (what happened, from the user's point of view), **原因** (why), **対処** (what to do next).

- ✅ 「CSVファイルを取り込めませんでした。文字コードの形式が正しくありません。UTF-8形式で保存して、もう一度取り込んでください。」
- ✅ 「8文字以上のパスワードを設定してください。」 · ❌ 「パスワードが短すぎます」 (Apple — say how to succeed)
- ✅ 「氏名をカタカナで入力してください。」 · ❌ 「入力に誤りがあります」 (デジタル庁)
- ✅ 「正しいメールアドレスの形式で入力してください」 · ❌ 「メールアドレスが間違っています」 (GovTech東京)
- Titles: never just 「エラー」 or 「エラー329347が発生しました」 (Apple). 「エラーが発生しました」 alone isn't enough; add the cause and action: 「通信エラーが発生しました。インターネット接続を確認してください。」
- Explain in user language, not system language: ❌ 認証に失敗しました → ✅ 「一定時間操作がなかったため、安全のためにログアウトしました。もう一度ログインしてください。」
- Don't repeat the object three times in one message (SmartHR).

### 8.2 Avoid 不正 and 無効 for "invalid"

不正 suggests fraud or wrongdoing; 無効 is mechanical.

- ❌ 電話番号が不正です → ✅ 電話番号の桁数をご確認ください。
- ❌ 入力値が無効です → ✅ 半角数字で入力してください。
- ❌ パスワードが無効です → ✅ パスワードが一致しません。
- ❌ 不正なメールアドレスです → ✅ メールアドレスの形式で入力してください（例：name@example.com）。

### 8.3 失敗しました vs できませんでした

- SmartHR uses 「{対象}の{操作}に失敗しました。」 for system failures; wordrabbit considers 失敗 cold. Microsoft maps "Failed to" → 〜できませんでした, "Cannot" → 〜できません, "Cannot find" → 〜が見つかりません.
- **Default for consumer copy:** 〜できませんでした (保存できませんでした。通信状況を確認して、もう一度お試しください。). 失敗 is acceptable in status labels and B2B.

### 8.4 Useful templates (adapted from SmartHR)

- Retryable server error: 「{対象}を{操作}できませんでした。時間をおいて、もう一度お試しください。」
- Not allowed: 「{条件}の{対象}は{操作}できません。」
- Over a limit: 「{上限}を超える{対象}は{操作}できません。」
- Already done: 「{対象}はすでに{操作}済みです。」
- Required field empty: 「{項目}を入力してください。」 / 「{項目}を選択してください。」
- No permission: 「アクセス権限がありません。」
- Unknown: 「予期しないエラーが発生しました。時間をおいて、もう一度お試しください。」

### 8.5 Apologies

- Apologize **only when the service is at fault** (outage, server error, lost data), **once**, as its own sentence. Don't apologize for input errors; explain the fix.
- 申し訳ございません (more formal; Microsoft for serious errors, commerce) vs 申し訳ありません (公用文 avoids ございます). Both are accepted today. Pick one per product.
- ✅ 「申し訳ありません。現在サービスを利用できません。しばらくしてから、もう一度お試しください。」
- ❌ every validation message starting with 「申し訳ございませんが…」.
- お手数ですが／恐れ入りますが soften requests that cost effort; use sparingly.
- Never 「！」 in errors.

---

## 9. Other microcopy

### 9.1 Confirmation dialogs

- Title = the question with the object named, in 〜しますか？; body = the consequence; buttons = キャンセル + the verb.
  - ✅ 「下書きを削除しますか？」 / 「削除した下書きは元に戻せません。」 [キャンセル][削除]
  - ❌ 「本当によろしいですか？」 [いいえ][はい]
- "Are you sure you want to X?" → 「Xしますか？」. 「Xしてもよろしいですか？」 is more deferential and acceptable in commerce. Avoid 本当に, which sounds like doubting the user.
- For irreversible actions, say so: 「この操作は取り消せません。」

### 9.2 Completion, toasts, progress

- Completion uses the past tense, active voice with the system as implied actor: 保存しました · 送信しました · コピーしました · 変更を保存しました.
- Don't translate "successfully": ❌ 正常に保存されました → ✅ 保存しました. "Success!" → 完了しました / 登録が完了しました, not 成功！
- Passive is fine when a third party acted: 承認されました · 山田さんが参加しました.
- Snackbar undo: 元に戻す.
- Progress: 〜中… or 〜しています…: 読み込み中… · 送信中… · アップロード中（3/10） · ファイルをコピーしています…. "Please wait" → しばらくお待ちください。

### 9.3 Status labels (SmartHR patterns)

○○前 · 未○○ · ○○中 · ○○待ち · ○○済み · ○○完了 · 要○○ · ○○失敗 — e.g., 開始前, 未送信, 入力中, 承認待ち, 設定済み, 完了, 要確認.

### 9.4 Forms

- **Labels**: plain nouns — 氏名, メールアドレス, 電話番号, 生年月日, 郵便番号, 住所. Don't copy stiff terms from paper forms (GovTech東京).
- **Required / optional (disagreement)**: デジタル庁 uses 「※必須」「※任意」; GovTech東京 says write 「必須」 as text and don't rely on ※ or ＊ alone (screen readers); many apps use a 【必須】 badge; SmartHR's term is 「入力必須」, not 「必須入力」. **Default:** a text tag 「必須」／「任意」 next to the label, marking whichever is the minority — never a symbol or color alone.
- **Placeholders**: not a substitute for a label or help text (デジタル庁). GovTech東京 puts examples above the field instead of in the placeholder; Apple allows format hints. When used: 「例：山田 太郎」 (no 。).
- **Help text**: です・ます with 。: 「半角英数字8文字以上で入力してください。」
- **Accept input flexibly** and say what's accepted: full/half width, with or without hyphens (GovTech東京).

### 9.5 Empty states and onboarding

- Say what goes here and how to start: 「まだメッセージはありません」 + 「メンバーを招待して、会話を始めましょう。」 [メンバーを招待].
- Prefer affirmative wording; avoid double negatives (Ubie): ✅ 病気の特定には医師による診断が必要です。 ❌ 症状チェックだけでは病気の特定は行われません。
- No guilt or pressure; frame progress (Mercari).

### 9.6 Links and accessibility

- Describe the destination: ✅ 料金プランを見る · ヘルプを参照する · ❌ 詳細はこちら (SmartHR). For "Learn more": 詳細 (neutral), 詳しく見る (prominent CTA).
- Navigation particles: に for specific places (一覧に戻る), へ for relative positions (前へ, 次へ) (SmartHR).
- Don't rely on position or color: ✅ 続けるには「次へ」を選んでください。 ❌ 下のボタンを押してください。 · ✅ 次の症状 ❌ 以下の症状 (Ubie).

### 9.7 Notifications

- Titles are noun phrases or short past-tense statements: 新しいメッセージ · 山田さんがコメントしました. Bodies use です・ます.

---

## 10. Names, addresses, dates, numbers, prices

### 10.1 Names

- Family name first: 山田 太郎 (SmartHR writes a half-width space between 姓 and 名). Official romanization is also family-name-first since 2020 (YAMADA Taro).
- Fields: separate 姓／名 fields are common; GovTech東京 recommends a single field with an example 「例：東京 花子」 and generous length for foreign names. Label as 姓・名 or 名字・名前 (friendlier).
- Reading field: the label's script signals the expected script — フリガナ (katakana) vs ふりがな (hiragana); SmartHR uses ヨミガナ. Say it explicitly: 「カタカナで入力してください。」 and ideally accept both.
- Honorifics: 様 in commerce and account screens, さん in collaborative B2B, none in data lists.

### 10.2 Addresses and phones

- Large → small: 〒160-0023 東京都新宿区西新宿2-8-1 ○○ビル5階. The label is 郵便番号; offer postal-code auto-fill.
- Accept phone numbers with or without hyphens (例：09011112222).

### 10.3 Dates and times

- Running text: 2026年9月23日; tables: 2026/09/23; with weekday: 2026年9月23日(水). Pick full- or half-width weekday parentheses and stay consistent.
- 24-hour clock with a colon (SmartHR): 14:30. 午前／午後 is fine in conversational consumer copy (午後3時). Avoid 午前12時／午後12時 ambiguity — write 正午 or 0:00 / 12:00.
- 西暦 by default. Add 和暦 only where documents or legal contexts need it: 2026年（令和8年）. GovTech東京 asks for 西暦-only input.
- Ranges with a wave dash: 9月1日〜9月30日.
- Relative: たった今 · 5分前 · 昨日 · 3日前.

### 10.4 Numbers and counters

- Half-width Arabic numerals in horizontal UI: ✅ 1つ選んでください ❌ 一つ選んでください. Kanji numerals only in fixed words (一覧, 一部, 一時的).
- Commas every three digits: 1,000,000円. With 万／億, no commas in the 万 part: 3万8000人 (JTF). Use 万/億 for approximate large counts (再生回数 1.2万回); full figures for money and exact data.
- **No plurals, but choose the right counter**: 件 (records, notifications: 3件の通知), 人／名 (people; 名 more formal), 個, 枚 (images, cards, sheets), 回 (times), 点 (items in a set), 台 (devices), 通 (emails, letters), か月.
- Zero usually needs different copy: ✅ 通知はありません ❌ 0件の通知. Selection: 3件選択中.

### 10.5 Prices

- 1,000円 is the most natural in running text; ¥1,000 is common on price tags and stores.
- **Consumer prices must show the tax-inclusive total** (総額表示義務, since April 2021): ✅ 10,780円 · 10,780円（税込） · 10,780円（税抜価格9,800円） · ❌ 9,800円（税抜） alone.

---

## 11. Translating from English: pitfalls

| English | ❌ Literal | ✅ Natural |
|---|---|---|
| Your profile | あなたのプロフィール | プロフィール |
| We value your privacy | 私たちはあなたのプライバシーを大切にします | プライバシーの保護に努めています。 |
| Your file has been uploaded | あなたのファイルはアップロードされました | ファイルをアップロードしました |
| Saved successfully! | 正常に保存されました！ | 保存しました |
| Success! | 成功！ | 完了しました |
| Oops! Something went wrong | おっと！何かがうまくいきませんでした | 問題が発生しました。もう一度お試しください。 |
| Are you sure you want to delete this? | 本当にこれを削除してもよろしいですか？ | このファイルを削除しますか？ |
| Invalid email | 無効なメール | メールアドレスの形式で入力してください。 |
| Please enter your name | どうぞ名前を入力してください、お願いします | 名前を入力してください。 |
| Click here to learn more | 詳しく知るにはここをクリック | 料金プランの詳細を見る |
| Page not found | ページが見つかりませんでした！ | ページが見つかりません |

Also:

- **Title Case has no equivalent** — don't try to emulate it.
- **"Please"** is already inside 〜してください; don't add どうぞ／お願いします on top.
- **Exclamation-heavy English marketing** → calmer Japanese with 。.
- **Double negatives** in English sources → make them affirmative.
- **Length:** Japanese often has fewer characters than English, but each is about twice as wide, so pixel width is similar. Long katakana (アクセシビリティ設定) and kanji compounds can't hyphenate. Keep sentences under ~50 characters where possible (Ubie: 80 max), one idea per sentence (公用文: 一文一義).
- **Line breaks:** Japanese has no spaces, so text can break mid-word (保存しま／す, アカウ／ント). Write short labels that fit on one line; for headings, break at phrase (文節) boundaries — Google's BudouX does this automatically. Never start a line with 、。）」 or ー. When truncation is possible, put the distinguishing word first.

---

## 12. Glossary

| English | Japanese | Notes |
|---|---|---|
| Settings | 設定 | |
| Done | 完了 | |
| Cancel | キャンセル | Always, for the button that backs out (Apple) |
| OK | OK | Half-width; informational alerts only. Not オーケー |
| Back / Next / Previous | 戻る / 次へ / 前へ | 「一覧に戻る」 |
| Continue | 続ける / 続行 | 次へ in step flows |
| Save / Delete / Edit | 保存 / 削除 / 編集 | |
| Remove | 削除 · 外す · 除外 · 解除 | Choose by meaning (§3.3) |
| Add / Create | 追加 / 作成・新規作成 | |
| Share | 共有 | シェア in consumer social |
| Search | 検索 | Placeholder: キーワードで検索 |
| Undo / Redo | 元に戻す / やり直す | |
| Copy / Duplicate | コピー / 複製 | |
| Close / Dismiss | 閉じる | |
| Got it | OK / わかりました | 了解 is too curt for most UI |
| Skip | スキップ | |
| Learn more | 詳細 / 詳しく見る | Prefer a descriptive link |
| Get started | はじめる / 今すぐはじめる | |
| Sign up | 新規登録 / アカウントを作成 / 会員登録 | |
| Log in / Log out | ログイン / ログアウト | Apple and Microsoft use サインイン／サインアウト |
| Forgot password? | パスワードをお忘れの方 / パスワードをお忘れですか？ | |
| Notifications | 通知 / お知らせ | 通知 = push/system; お知らせ = announcements |
| Account | アカウント | Never 口座 |
| Profile / Password | プロフィール / パスワード | |
| Import / Export | 取り込む・インポート / 書き出す・エクスポート | |
| Enable / Disable | 有効にする / 無効にする | |
| Submit | 送信 / 提出 / 申請 / 確定 | Forms 送信; HR 提出; government 申請 |
| Apply (filter) | 適用 | |
| Confirm | 確認 / 確定 | 確認 = review; 確定 = finalize (注文を確定する) |
| Required / Optional | 必須 / 任意 | |
| Loading… | 読み込み中… | |
| Retry | 再試行 / もう一度試す | Body: もう一度お試しください。 |
| Contact us | お問い合わせ | |
| Privacy policy / Terms | プライバシーポリシー / 利用規約 | |
| Cart / Checkout | カート / レジに進む・購入手続きへ | |

---

## 13. Product-level policies to decide (and record in the glossary)

1. Space between Japanese and Latin/digits: **none** (default) or half-width.
2. Final ー: **内閣告示 style** (ユーザー) (default) or platform style (ユーザ); the *-y* word policy.
3. Katakana compound separator: **none** (default), half-width space, or ・.
4. Buttons: **bare verbal noun** (保存) (default) vs 〜する.
5. Symbol widths: （）：？ full-width (default) vs half-width; % vs ％.
6. UI element quoting in text: 「 」 or ［ ］.
7. Required marker: text 「必須」／「任意」 (default), ※必須, or 【必須】.
8. すべて／すでに vs 全て／既に; わかる vs 分かる.
9. 申し訳ありません vs 申し訳ございません.
10. ログイン vs サインイン.
11. 。 on toasts or not.

---

## 14. Review checklist

- [ ] Sentences in です・ます; labels, titles and buttons are nouns or plain forms; no mixing.
- [ ] No excessive keigo, させていただく, 二重敬語 or バイト敬語; お／ご only where natural and never on buttons or katakana.
- [ ] No あなた; subjects and possessives omitted.
- [ ] Buttons: short, specific verb, no polite form, no 。; キャンセル for cancel; no はい/いいえ; OK only in informational alerts.
- [ ] Errors: what happened + why + what to do; no 不正／無効; no blame; apology only for service faults; no ！.
- [ ] Completion messages in past tense; no 正常に; no literal "Oops" or "Success!".
- [ ] Katakana ー and separators follow the product policy; no half-width katakana; no unnecessary katakana.
- [ ] 形式名詞 and 補助動詞 in hiragana (こと, とき, ください, いただく, できる).
- [ ] Half-width digits and Latin; spacing policy applied; wave dash for ranges; brackets consistent.
- [ ] Names family-first; dates 2026年9月23日 or 2026/09/23; 24-hour time; correct counters; zero-count phrased naturally; tax-inclusive prices.
- [ ] No reliance on position or color; descriptive link text; labels fit on one line or break at phrase boundaries.

---

## 15. Sources

- SmartHR Design System — ライティング（表記ルール、用字用語、UIテキスト、エラーメッセージ）: https://smarthr.design/products/contents/
- JTF日本語標準スタイルガイド（翻訳用）第3.0版: https://www.jtf.jp/pdf/jtf_style_guide.pdf
- Microsoft Japanese Localization Style Guide: https://learn.microsoft.com/globalization/reference/microsoft-style-guides ; 2008 long-vowel change: https://pc.watch.impress.co.jp/docs/2008/0725/ms.htm
- Apple Human Interface Guidelines（日本語）表現・アラート: https://developer.apple.com/jp/design/human-interface-guidelines/writing ; https://developer.apple.com/jp/design/human-interface-guidelines/alerts
- 文化審議会「公用文作成の考え方（建議）」令和4年: https://www.bunka.go.jp/seisaku/bunkashingikai/kokugo/hokoku/93657201.html
- 内閣告示「外来語の表記」(1991): https://www.bunka.go.jp/kokugo_nihongo/sisaku/joho/joho/kijun/naikaku/gairai/index.html
- 文化審議会「敬語の指針」(2007): https://www.bunka.go.jp/seisaku/bunkashingikai/kokugo/hokoku/pdf/keigo_tosin.pdf
- デジタル庁デザインシステム（インプットテキスト）: https://design.digital.go.jp/dads/components/input-text/usage/
- GovTech東京「入力フォームガイダンス」: https://www.digitalservice.metro.tokyo.lg.jp/
- Ubie Vitals UXライティング: https://vitals.ubie.life/ux-writing/
- Goodpatch「キャンセルのキャンセル問題から考えるダイアログデザイン」: https://goodpatch.com/blog/dialog-design
- wordrabbit UXライティングガイド: https://wordrabbit.jp/
- Mercari UX writing interview: https://www.unprinted.design/articles/mercari-ux-writing-for-brand-experience-clarity/
- 「『電話番号が不正です』はもうやめよう」(Qiita): https://qiita.com/masamis/items/474d02bbd5926137b429
- 財務省 総額表示: https://www.mof.go.jp/tax_policy/summary/consumption/210107leaflet_sougaku.pdf
- Google BudouX: https://github.com/google/budoux