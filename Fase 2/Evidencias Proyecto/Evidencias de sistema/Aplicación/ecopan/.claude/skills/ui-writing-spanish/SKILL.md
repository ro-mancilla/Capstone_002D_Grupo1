---
name: "ui-writing-spanish"
description: "Write, review or translate into Spanish the UI labels and microcopy of mobile/web apps (buttons, dialogs, errors, forms, toasts), neutral LatAm default with Spain, voseo, Mexico and Chile variants."
---

# Spanish UI writing (labels and microcopy)

Use this skill whenever you write, edit, review or translate *into Spanish* any text shown inside a mobile or web product: buttons, menus, tabs, titles, dialogs, form labels, hints, errors, toasts, notifications, empty states, onboarding, settings and links.

It covers **content and language only**: register, grammar, word choice, spelling, punctuation, numbers and regional variation. It ignores string-file formats and i18n code.

**Default variety: neutral Latin American Spanish** (es-419): *tú*, LatAm vocabulary (celular, computadora, agregar, configuración), preterite for recent events. §12 covers Spain, Rioplatense voseo, Mexico, Chile, Colombia and US Spanish.

Rules are synthesized from the Real Academia Española (Diccionario panhispánico de dudas, *Ortografía de la lengua española* 2010, *Libro de estilo* 2018, *Español al día*), the FundéuRAE network (Fundéu Guzmán Ariza, Fundéu Argentina, FundéuBBVA), Microsoft and Apple Spanish localization practice, Google's es-419 guidance, UN inclusive-language guidelines, and public plain-language standards from Argentina (argentina.gob.ar) and Colombia (DNP). Where sources disagree, the skill says so and recommends a default.

---

## 1. Workflow

1. **Fix the locale and register first**: which country or "neutral LatAm"? *tú*, *usted* or *vos*? If the product has a glossary or style guide, it wins. Otherwise use the defaults here and state them.
2. **Identify the component**: each has its own verb form and punctuation (§3, §5).
3. **Write natively; don't calque English.** Translate the intent, not the words. Spanish prefers active voice or *se* constructions, verbs over noun stacks, and fewer possessives.
4. **Check agreement**: gender and number of every article, adjective and participle, especially around variables and counts (§7).
5. **Check spelling mechanics**: tildes (including on capitals), ñ, opening ¿ ¡, sentence case (§4, §6).
6. **Check length**: Spanish is longer than English (§9).
7. Run the checklist in §14. When reviewing, give ❌ → ✅ rewrites and name the rule.

---

## 2. Register: tú, usted, vos

- **Default for consumer apps: *tú*.** Apple, Google and current Windows UI all use *tú*, in both Spain and Latin America. Google's es-419 guidance asks for "un tono amigable y familiar" and warns that too much formality sounds distant.
- **Use *usted*** when the brand or market asks for it: government, some banking, health, insurance, legal, and markets where ustedeo is the norm (large parts of Colombia and Central America — test with local users). Usted is not automatically "more polite" in an app; in consumer products it can feel bureaucratic.
- **Use *vos* only for Argentina/Uruguay builds** (optionally Paraguay and parts of Central America). The RAE says voseo "goza de total aceptación en la norma culta" in the Río de la Plata. Never use vos in a neutral build.
- **Never mix registers** in a product — pronouns, possessives, verb forms and clitics must all agree.
  - ❌ "Ingrese su correo y confirma tu contraseña."
  - ✅ tú: "Ingresa tu correo y confirma tu contraseña."
  - ✅ usted: "Ingrese su correo y confirme su contraseña."
  - ✅ vos: "Ingresá tu correo y confirmá tu contraseña."
  - A real anti-pattern: Microsoft Support pages written in *usted* that quote Windows UI strings in *tú*. Keep help content and UI in the same register.
- **Usted's *su* is ambiguous** (his/her/your/their). Rephrase when needed: "Revise la bandeja de entrada de su correo".
- **Plural address:** Latin America uses *ustedes* for all plural address. Spain uses *vosotros* informally ("Invitad a vuestros amigos", "Os damos la bienvenida"). Most apps address one person, so this only matters for group features.

---

## 3. Buttons, commands and verb forms

The most important convention: **controls name the action with an infinitive; sentences address the user with an imperative.**

The RAE rejects the infinitive used as a direct order to someone ("¡Callar!"), but accepts it in impersonal instructions ("No fumar", "Lavar a mano"). A button *names* an action rather than ordering someone, which is why infinitive buttons are standard (Microsoft and Apple practice). A sentence addressed to the user needs a real imperative.

| Context | Form | ✅ | ❌ |
|---|---|---|---|
| Buttons, menu items, toolbar commands | Infinitive (or noun) | Guardar · Cancelar · Eliminar · Iniciar sesión · Crear cuenta · Ver más · Descargar e instalar | Guarda · Guardando · Salvar · Clic aquí |
| Screen and dialog titles | Noun phrase or infinitive, **never a gerund** | Cambiar contraseña · Instalación de la impresora · Configuración de privacidad | Cambiando tu contraseña · Instalando la impresora |
| Instructions, body text, errors | Imperative in the chosen register | "Ingresa el código que te enviamos." · "Toca **Guardar** para confirmar." | "Ingresar el código que le enviamos." (infinitive aimed at the user) |
| Live status | Gerund is fine | Guardando… · Cargando… · Enviando mensaje… | (as a title) |
| Marketing CTAs, banners | Imperative acceptable | Descarga la app · Prueba gratis 30 días | Mixing imperative and infinitive in the same button group |
| Checkbox / toggle | Infinitive, noun, or statement | Recordarme · Recibir notificaciones · Modo oscuro | "¿Quieres recordar?" |

Rules for buttons:

1. **Be specific**: "Guardar cambios", "Descargar PDF", "Continuar al pago" beat a bare "Aceptar".
2. **Dialog buttons repeat the verb of the question**: "¿Eliminar 3 archivos?" → [Cancelar] [Eliminar]. Avoid [Sí] [No] and avoid [Aceptar] for destructive actions.
3. **No final period**, sentence case, and no gerunds.
4. **One style per component type.** If CTAs use imperatives ("Regístrate gratis"), all CTAs do; controls stay infinitive.
5. **Soft decline**: "Ahora no" or "Más tarde" read better than "No".

---

## 4. Capitalization: sentence case, always

- **Spanish doesn't use Title Case.** The RAE: only the first word of a title takes a capital; "no es necesario ni correcto" to capitalize every significant word.
  - ✅ "Configuración de la cuenta" · "Métodos de pago" · "Crear cuenta nueva"
  - ❌ "Configuración De La Cuenta" · "Métodos De Pago" · "Crear Cuenta Nueva"
  - Don't copy vendor residue such as Apple Spain's "Actualización de Software" or "Descargar e Instalar". Apple Latin America writes "Actualización de software".
- **Lowercase** days, months, seasons, languages, nationalities and currency names: "el lunes 3 de marzo", "cambiar a inglés", "pagar en pesos chilenos", "euros". They get a capital only as the first word of a string ("Español (México)" as a language-picker item is fine).
- **Capitals keep their tildes** (RAE): "ÁREA", "ÚLTIMOS DÍAS", "MÁS VENDIDOS". If a design uses uppercase styling, the source text must still be correctly accented, and the font must support the glyphs.
- **Avoid ALL CAPS** in text. The *Libro de estilo* says it "puede entenderse como un grito".
- **Product and feature names**: capitalize only the proper name. "la app Mail", "tu cuenta de Google", "la pestaña Explorar". Follow the product glossary when a brand treats a feature as a proper name.
- **internet, wifi**: FundéuRAE prefers lowercase "internet" and "wifi". The RAE also accepts "Internet" as a proper noun, and Apple LatAm writes "Internet" and "Wi-Fi". **Default: lowercase "internet"; follow the platform for Wi-Fi/wifi.** Be consistent.
- **After a colon**: generally lowercase ("Nota: los cambios se aplicarán mañana."). UI labels like "Nota:" or "Importante:" followed by a capital are a common house style but not RAE-backed. Decide once.

---

## 5. Punctuation

### Opening ¿ and ¡ are mandatory

- The DPD requires the opening marks and warns against omitting them "por imitación de otras lenguas". The *Libro de estilo* tolerates dropping them only in informal chats; product UI is not a chat.
  - ❌ "Quieres guardar los cambios?" · "Listo!" · "Bienvenido!"
  - ✅ "¿Quieres guardar los cambios?" · "¡Listo!" · "¡Te damos la bienvenida!"
- They open **where the question starts**, which may be mid-sentence: ✅ "Ana, ¿quieres activar las notificaciones?" · ✅ "Si sales ahora, se perderán los cambios. ¿Quieres salir?"
- **Never put a period after ? or !** ❌ "¿Eliminar archivo?."
- A word following a closing ? or ! that ends the sentence is capitalized. If the sentence continues, it stays lowercase.

### Periods

- **No period** in buttons, menu items, tabs, titles, field labels or fragment-style toasts ("Cambios guardados"). The DPD says isolated titles take no final period.
- **Period** in full sentences and multi-sentence bodies: "No pudimos guardar los cambios. Revisa tu conexión e inténtalo de nuevo."
- The period goes **after** closing quotes, parentheses and brackets: «Guardar». / (opcional).
- An emoji at the end does not replace the period (*Libro de estilo*).

### Commas

- **No serial comma** before *y*, *o* or *ni* in a simple list: ✅ "Fotos, videos y documentos" · ❌ "Fotos, videos, y documentos".
- **The vocative comma is required**: ✅ "Hola, Ana" · "Gracias, Juan" · ❌ "Hola Ana".
- **No comma between subject and verb**: ❌ "Los archivos seleccionados, se eliminarán."
- "Por favor" is followed by a comma when it opens a sentence: "Por favor, espera un momento."

### Colon

- Lowercase after it in running text. Never put a colon between a preposition and its object: ❌ "Disponible en: iOS y Android" → ✅ "Disponible en iOS y Android".
- Formal greetings take a colon: "Hola, Ana:" / "Estimada Ana:" followed by a new line. A comma is common in informal email.

### Quotation marks

- The RAE prefers angular quotes « » first, then " ", then ' '.
- In LatAm UI practice, " " is widespread, and Apple and Google use **bold** for UI element names ("Toca **General**").
- **Default:** don't quote UI element names (bold them in help content, or just name them). For quoting user content, use « » in es-ES editorial style and " " in LatAm. Pick one per product.

### Ellipsis, dashes and hyphens

- Use exactly three dots, attached to the previous word, then a space: "Cargando…", "Guardar como…". Never "Cargando ..." or "....". Don't combine it with *etc.* (DPD).
- In UI, prefer commas or parentheses to the raya (—). The hyphen (-) is for compounds and ranges ("10-12"). Never use a spaced hyphen " - " as a dash.

---

## 6. Tildes, ñ and common misspellings

- **Accent every word that needs one**, capitals included. Frequent UI misspellings: ❌ Busqueda, Configuracion, Informacion, Contrasena, Ubicacion, Telefono, Numero, Codigo, Pagina, Menu, Categoria, Envio, Direccion, Sesion, Notificacion → ✅ Búsqueda, Configuración, Información, Contraseña, Ubicación, Teléfono, Número, Código, Página, Menú, Categoría, Envío, Dirección, Sesión, Notificación.
- **Never drop the ñ.** "ano" for "año" is a notorious error. The same goes for contraseña, diseño and España.
- **Diacritic tildes that UI copy often gets wrong:**
  - más / mas: ✅ "Ver más"
  - sí / si: ✅ "Sí, eliminar"
  - tú / tu: ✅ "Tú eliges" · "tu cuenta"
  - aún / aun: ✅ "Aún no tienes pedidos"
  - qué, cómo, cuándo, dónde, quién in direct and indirect questions: ✅ "Descubre cómo funciona" · "Elige dónde guardar"
- **solo, este, ese, aquel take no tilde.** The RAE recommends never using it: ✅ "Solo para miembros" · ❌ "Sólo para miembros".
- **Monosyllables take no tilde** (2010 *Ortografía*): guion, fue, dio, vio, ti.
- **Plurals move or drop the tilde**: sesión → sesiones, canción → canciones, imagen → imágenes, examen → exámenes, carácter → caracteres. So never generate plurals by appending "s"; write each form out.
- **Voseo forms carry their own tildes**: Ingresá, Tocá, Elegí, Querés, Podés, but "sos" has none. With clitics, tildes change: tú *Ingrésalo* / vos *Ingresalo*; tú *Inténtalo* / vos *Intentalo*.
- **Symbols never take tildes or periods**: "km", "kg", "min" as symbols. Hashtags keep accents: #FelizDíaDelNiño.

---

## 7. Grammar pitfalls

### 7.1 Agreement with variables and counts (the biggest localization bug)

Articles, adjectives and participles agree in gender and number. Write each variant out in full.

- ❌ "Se eliminó {n} archivos" → ✅ "Se eliminó 1 archivo" / "Se eliminaron 3 archivos"
- ❌ "Tienes {n} mensaje(s) nuevo(s)" → ✅ "Tienes 1 mensaje nuevo" / "Tienes 5 mensajes nuevos"
- ❌ "{item} guardado" when {item} may be "la foto" → ✅ separate strings ("Foto guardada", "Documento guardado") or restructure ("Se guardó: {item}")
- ❌ "El {función} está desactivado" when the value is "ubicación" → assign each feature name a fixed gender in the glossary, or restructure ("Desactivaste {función}")
- **Zero is plural** ("0 archivos"), but it's nicer to rephrase: "No hay archivos".
- **Contractions**: *de + el = del*, *a + el = al*. Concatenation produces ❌ "Ir a el inicio" instead of ✅ "Ir al inicio".
- **Never concatenate fragments** ("Eliminar" + "{objeto}"). The article changes: "Eliminar **la** foto" vs "Eliminar **el** video".

### 7.2 Voice and tense

- **Avoid the English-style periphrastic passive.** Use active voice, the *se* passive, or a participle fragment.
  - ❌ "El archivo ha sido eliminado." → ✅ "Se eliminó el archivo." / "Eliminamos el archivo." / "Archivo eliminado"
  - ❌ "Su solicitud está siendo procesada." → ✅ "Estamos procesando tu solicitud."
  - ❌ "El formulario debe ser completado" → ✅ "Completa el formulario" (argentina.gob.ar: "Completá el formulario")
- **Recent past: Latin America uses the preterite, Spain the present perfect.**
  - LatAm: "Te enviamos un código", "Se guardó"
  - Spain: "Te hemos enviado un código", "Se ha guardado"
  - Participle fragments ("Código enviado", "Cambios guardados") work everywhere.

### 7.3 Gerunds

- The gerund is fine for live status ("Sincronizando…").
- Don't use it as an adjective: ❌ "Archivo conteniendo errores" → ✅ "Archivo con errores".
- Avoid the gerund of posteriority: ❌ "El pago falló, cancelándose el pedido" → ✅ "El pago falló y se canceló el pedido."

### 7.4 Calques and stylistic traps

- **Possessives**: Spanish uses fewer than English.
  - ❌ "Actualiza tu app a su última versión" → ✅ "Actualiza la app a la última versión"
  - In labels, drop them: "Editar perfil", "Métodos de pago".
- **English word order and noun stacks**:
  - ❌ "Ingresa un válido correo" → ✅ "Ingresa un correo válido"
  - ❌ "Error de conexión de servidor de red" → ✅ "No pudimos conectarnos al servidor"
- **Dequeísmo and queísmo**:
  - ✅ "Asegúrate **de** que…" · "Te informamos **de** que…" · "¿Estás seguro **de** que…?"
  - ❌ "Pienso de que…"
- **"a nivel de"**: the DPD rejects it in the sense "regarding". Use "en" or "con respecto a". Likewise "en base a" → "con base en", "según" or "a partir de".
- **"el mismo / la misma" as a pronoun**: ❌ "Deberá presentar el mismo" → ✅ "Preséntalo".
- **Positive framing**: ❌ "Si no has perdido tu derecho" → ✅ "Si conservas tu derecho".

---

## 8. Vocabulary, anglicisms and false friends

### 8.1 Recommended tech terms (RAE/FundéuRAE)

| English | Use | Avoid |
|---|---|---|
| log in | **iniciar sesión**, acceder, entrar, ingresar | loguearse, hacer login |
| sign up | **crear cuenta**, registrarse | |
| log out | **cerrar sesión** ("Salir" means leaving a screen, not ending the session) | desloguearse |
| click | **hacer clic** (clic, never "click"), cliquear; *clicar* in Spain | clickear, dar click |
| tap | **tocar** (Spain also *pulsar*; Apple now uses "toca" everywhere) | tapear |
| long press | **mantener pulsado**, mantener presionado | |
| swipe / scroll | **deslizar**, desplazarse | hacer scroll, scrollear |
| drag | arrastrar | |
| email | **correo electrónico**, correo | e-mail, mail, correo-e |
| app | **aplicación**, app (lowercase) | |
| Wi-Fi | **wifi** (Fundéu) or Wi-Fi (platform) | |
| smartphone / tablet | teléfono, celular / móvil · tableta | |
| online | en línea, conectado | |
| backup | **respaldo** (LatAm) · **copia de seguridad** (Spain) | backup |
| password | **contraseña** (*clave* in banking or PIN contexts) | password |
| link | **enlace**, vínculo | link (in UI text) |
| check | revisar, comprobar, verificar | chequear (accepted, not preferred) |

For device-agnostic help text, use *selecciona* or *elige*, which suit touch and mouse alike (Google recommends action verbs over gestures). Use *toca* or *haz clic* only when the device is known.

### 8.2 False friends and calques

| Avoid (meaning intended) | Use | Note |
|---|---|---|
| salvar (save a file) | **guardar** | *salvar* means to rescue |
| resumir (resume) | **reanudar**, continuar | *resumir* means to summarize |
| eventualmente (eventually) | finalmente, con el tiempo | The DPD calls it a "calco censurable" |
| actualmente (actually) | en realidad | *actualmente* means currently |
| soportar (support a format) | **admitir**, ser compatible con | *soportar* means to tolerate |
| librería (code library) | **biblioteca** | *librería* is a bookstore |
| customizar | **personalizar** | |
| setear (AR/UY colloquial) | configurar, establecer | |
| remover (remove) | **quitar**, eliminar | The current DPD accepts *remover*, but *quitar* is clearer and pairs with *eliminar* (see §8.3) |
| aplicar (apply for a job or card) | **solicitar**, postularse | The DPD accepts it in much of America; neutral copy should still prefer *solicitar*. *Aplicar un filtro* is correct |
| reportar (report a problem) | **informar de**, notificar; *denunciar* for abuse | "Reportar" is common in LatAm; Spain prefers "Informar de un problema" |
| asumir (assume) | suponer, dar por sentado | |
| Haz clic aquí | a descriptive link ("Consulta la política de privacidad") | |

### 8.3 Distinctions to keep

- **Eliminar** (delete permanently) vs **Quitar** (remove from a list or group; the object survives) vs **Borrar** (text, history, fields: "Borrar historial").
- **Activar / Desactivar** for settings. **Encender / Apagar** for devices. "Habilitar/Deshabilitar" is Microsoft-flavored and colder in consumer UI.
- **Cancelar** (abort this action) vs **Cerrar** (close a window) vs **Descartar** (drop unsaved changes or a suggestion).
- **Atrás** (the navigation control) vs **Volver** + destination (clearer as a button: "Volver al carrito").
- **Siguiente** (next item or step) vs **Continuar** (proceed in a flow; "Continuar al pago").

---

## 9. Length and text expansion

- Spanish runs **about 15–30% longer** than English for running text, and short labels can double or triple (the W3C/IBM table: English strings of up to 10 characters grow 200–300%). The W3C gives "FAQ" → "Preguntas frecuentes" as an example.
- Typical pairs: Sign in (7) → Iniciar sesión (14) · Settings (8) → Configuración (13) · Undo (4) → Deshacer (8) · Share (5) → Compartir (9) · Retry (5) → Reintentar (10) / Volver a intentar (17) · Learn more → Más información.
- **Ways to shorten without sounding telegraphic:**
  1. Choose the shorter valid synonym: "Reintentar" rather than "Volver a intentar"; "Ajustes" only where the platform uses it.
  2. Drop articles and possessives in labels: "Editar perfil", not "Editar tu perfil".
  3. Use participle fragments for toasts: "Enlace copiado", "Pedido cancelado".
  4. Use "Sin…" constructions: "Sin conexión" rather than "No hay conexión a internet disponible".
  5. Leave "por favor" out of tight strings.
  6. Use only standard abbreviations: "N.º", "pág.", "máx.", "mín.", "aprox.". Never "Config." or "Notif." on buttons.
- **Prefer a design fix to a mutilated label.** A wrapped two-line button is better than "Iniciar ses…". Flag the layout rather than inventing an abbreviation.
- Plain-language targets (argentina.gob.ar): sentences of about 15 words, paragraphs of 1–3 sentences, titles of 8–10 words at most.

---

## 10. Messages: errors, confirmations, success, empty states, notifications

### Errors

Say what happened, why (if it helps) and what to do next, in everyday words. Never blame the user.

- ❌ "Error: operación fallida." → ✅ "No pudimos completar el pago. Inténtalo de nuevo."
- ❌ "Formato de email inválido" → ✅ "Ingresa un correo válido, como nombre@ejemplo.com"
- ❌ "Campo obligatorio no cumplimentado" → ✅ "Ingresa tu nombre"
- ❌ "Excepción de red (código 503)" → ✅ "Hay un problema con el servicio. Inténtalo más tarde." (put the code, if needed, in secondary text)
- ❌ "Ingresaste mal los datos" → ✅ "Revisa los datos marcados"
- ❌ "La contraseña suministrada es inválida" → ✅ "La contraseña no es correcta. ¿Quieres intentarlo de nuevo?"

**Voice:** first-person plural ("No pudimos guardar los cambios") is warm, takes responsibility and is common in LatAm consumer apps. The impersonal form ("No se pudieron guardar los cambios") is neutral. Pick one per product.

**Retry:** use "Inténtalo de nuevo" or "Vuelve a intentarlo" in body text (usted: "Inténtelo de nuevo"; vos: "Intentá de nuevo" / "Volvé a intentarlo"). Buttons say "Reintentar".

### Apologies and "por favor"

- **"Lo sentimos"**: once, only for real failures on the product's side. ✅ "Lo sentimos, no pudimos procesar tu pago." · ❌ "Lo sentimos, la contraseña debe tener 8 caracteres."
- **"Ups" / "¡Ups!"**: informal. Never use it for payment, security or data-loss errors.
- **"Por favor"**: Spanish imperatives are not rude in UI ("Ingresa tu correo" is normal). Adding "por favor" to everything, as English "please" often is, reads as padding. Keep it for real inconveniences: "Por favor, espera mientras terminamos la actualización."

### Confirmations

- Title as a question with an opening ¿ and a specific verb, a body stating the consequence, and buttons with that verb: "¿Eliminar la conversación?" / "Esta acción no se puede deshacer." / [Cancelar] [Eliminar]
- ❌ "¿Está seguro que desea continuar?" (queísmo, vague, and gendered) → ✅ "¿Quieres salir? Se perderán los cambios sin guardar."

### Success and toasts

- "Cambios guardados" · "¡Listo!" · "Te enviamos un correo a ana@ejemplo.com" (Spain: "Te hemos enviado…")
- Don't translate "successfully": ❌ "Se guardó exitosamente" → ✅ "Se guardó" / "Guardado".

### Empty states and onboarding

- Explain, then offer the action.
  - ✅ "Aún no tienes favoritos. Toca ♥ en un producto para guardarlo aquí."
  - ❌ "No hay datos."
- Lead with the benefit, then the request: ✅ "Activa las notificaciones para saber cuándo llega tu pedido." [Activar] [Ahora no]

### Notifications

- Short and specific, key information first, sentence case, no shouting: ✅ "Tu pedido va en camino. Llega hoy entre 14:00 y 18:00." · ❌ "¡¡URGENTE!! Tu pedido…"

### Links

- Descriptive, never "aquí" or "clic aquí": ✅ "Consulta la política de privacidad".

---

## 11. Inclusive language

- **The RAE position**: the generic masculine is the unmarked, inclusive form. Occasional doubling ("el presidente o la presidenta") is acceptable. The RAE does not endorse **@, x or -e**.
- **In UI, never use @ or x.** Screen readers read "tod@s" as "todarrobaese" and "todxs" as nonsense. The "-e" form is a polarizing brand decision; avoid it by default.
- **Doubling** ("todos y todas") clutters UI. The UN warns that overusing it harms readability.
- **The real trap is adjectives and participles aimed at the user** (bienvenido, seguro, listo, registrado, conectado, suscrito, invitado). Rewrite them with verbs, nouns, "tu/tus", epicene nouns, *quien* or impersonal forms (UN guidance):

| Gendered | Neutral |
|---|---|
| Bienvenido / Bienvenid@ | **Te damos la bienvenida** · Hola, {nombre} · ¡Hola! |
| ¿Estás seguro? | **¿Quieres continuar?** · ¿Confirmas que quieres salir? |
| Estás registrado | **Ya tienes una cuenta** · Tu registro está completo |
| Estás suscrito | **Tu suscripción está activa** |
| Conectado como Ana | **Sesión iniciada como Ana** |
| ¿Nuevo usuario? | **¿No tienes cuenta?** |
| Los usuarios pueden… | **Quienes usan la app** pueden… · Puedes… · Las personas usuarias… (formal, public sector) |
| Invita a tus amigos | Invita a **tus contactos** · a **otras personas** |
| Sé el primero en comentar | **Nadie ha comentado todavía** · Sé la primera persona en comentar |
| El usuario debe tener una cuenta | **Es necesario tener una cuenta** |

- If the product knows the user's grammatical gender, it can use gendered strings, but that requires separate strings per gender. Offer inclusive options in gender fields ("Mujer / Hombre / No binario / Prefiero no decirlo").

---

## 12. Regional variants

### 12.1 Vocabulary

| Concept | Neutral LatAm | Mexico | Argentina/Uruguay | Chile | Colombia | Spain |
|---|---|---|---|---|---|---|
| mobile phone | celular | celular | celular | celular | celular | **móvil** |
| computer | computadora | computadora | computadora | **computador** | **computador** | **ordenador** |
| add | **agregar** | agregar | agregar | agregar | agregar | **añadir** |
| enter (data) | ingresar / escribir | ingresar | ingresar | ingresar | ingresar | **introducir** |
| log in (button) | Iniciar sesión | Iniciar sesión | Ingresar / Iniciar sesión | **Ingresar** / Iniciar sesión | Ingresar / Iniciar sesión | Iniciar sesión / Acceder |
| Settings | Configuración | Configuración | Configuración | Configuración | Configuración | **Ajustes** (Apple, Android) |
| backup | respaldo | respaldo | copia de seguridad | respaldo | copia de seguridad | **copia de seguridad** |
| tap | tocar | tocar | tocar | tocar | tocar | tocar / pulsar |
| click | hacer clic | hacer clic, dar clic | hacer clic | hacer clic | hacer clic, dar clic | hacer clic, **clicar** |
| car | auto | carro, auto | auto | auto | carro | **coche** |
| ticket (transport) | boleto / pasaje | boleto | pasaje | pasaje | tiquete | **billete** |
| cart | carrito | carrito | carrito | carro / carrito | carrito | carrito / cesta |
| the Mac | la Mac | la Mac | la Mac | la Mac | la Mac | **el Mac** |

Keep slang out of every build: padre, chido, copado, bacán, altiro, chévere, guay, lana, plata as "money" in neutral copy.

### 12.2 Words to use with caution

- **coger** is vulgar in Mexico, Argentina, Uruguay, Venezuela and much of Latin America, though neutral in Spain. Never use it outside es-ES; use *tomar* instead.
- Other risky words: **pinche** (vulgar in Mexico), **concha** (vulgar in Argentina), **bicho** (vulgar in Puerto Rico).
- **ahorita** can mean "later" or "never" in Mexico. Never use it for timing.
- **vale** (OK) sounds Peninsular. In neutral copy use "Aceptar", "Entendido" or "OK".
- For photos, LatAm says "tomar una foto" and Spain "hacer una foto". "Tomar foto" is the neutral choice.

### 12.3 Rioplatense voseo (Argentina, Uruguay)

- **Affirmative imperatives** are stressed on the last syllable: Ingresá, Tocá, Elegí, Creá, Completá, Revisá, Descargá, Volvé, Hacé, Poné, Decí, Vení, Salí, Tené en cuenta. For *ir*, use **Andá** or rephrase ("Entrá a Configuración").
- **Present tense**: querés, podés, tenés, sos (no tilde).
- **Pronouns and possessives stay *te / tu***: "Te enviamos un código a tu correo".
- **Negative imperatives**: the educated norm (DPD) prefers tuteo forms: ✅ "No olvides", "No toques" rather than "No olvidés", "No toqués".
- **Clitics**: Ingresalo, Elegilo, Confirmalo, Intentalo (no tilde).
- **Where it's used**: Mercado Libre Argentina ("Completá los datos…") and argentina.gob.ar ("Completá el formulario") use voseo; Mercado Libre Mexico and Chile use tuteo. Global brands often ship neutral *tú* to Argentina. That's acceptable, but it feels foreign.
- **Don't mix**: ❌ "Ingresá tu correo y confirma tu contraseña."

### 12.4 Mexico

- *tú* in consumer apps.
- Vocabulary: celular, computadora, "dar clic" (very common) / "hacer clic", "checar" (informal only; use *revisar* in UI).
- Formats: decimal point, "$" means pesos, 12-hour clock.

### 12.5 Chile

- *tú* in apps. Chilean informal voseo ("¿cómo estái?") never appears in UI.
- "Ingresar" is common for log in. Vocabulary: computador, celular, pasaje, RUT (national ID).
- Formats: decimal comma, point grouping, no cents on CLP ("$12.990"). CLDR's short date is dd-mm-aa (07-03-26). The 24-hour clock is common.
- Keep Chilean slang (bacán, altiro, cachai) out of UI.

### 12.6 Colombia

- *usted* is widespread even in informal settings, and brands split between *tú* and *usted*. Decide from audience research. The DNP clear-language guide says to match register to audience.
- Vocabulary: computador, celular, carro, tiquete.
- Formats: decimal comma, "$" means pesos.

### 12.7 Spain (es-ES)

- *tú* in consumer apps. *usted* in public administration and some banking. *vosotros* for plural address.
- Vocabulary: móvil, ordenador, Ajustes, añadir, introducir, copia de seguridad, clicar/pulsar, coche, billete.
- Tense: present perfect for recent events ("Hemos enviado un código a tu correo").
- Formats: "12,99 €" (symbol after, with a space), "50 %", 24-hour clock, «comillas» in editorial text.

### 12.8 US Spanish (es-US)

- Neutral LatAm with a Mexican lean (celular, computadora, carro).
- Avoid Spanglish calques (aplicar for "apply for", "llamar para atrás").
- Formats: US conventions (decimal point, $, 12-hour clock). Spell out months to avoid MM/DD vs DD/MM confusion: "7 de marzo de 2026".
- *usted* is common in government and health services.

---

## 13. Numbers, dates, currency and units

**Follow the locale's conventions; never mix systems on one screen.**

- **Decimal separator:** the RAE (2010) *recommends* the point "to promote unification" but accepts both. Real practice, and CLDR, follow the country:
  - **Comma**: Spain, Argentina, Chile, Colombia, Uruguay, Paraguay, Ecuador (and Peru per the RAE; CLDR uses a point for Peru)
  - **Point**: Mexico, Central America, Dominican Republic, Puerto Rico, US Spanish
- **Thousands:** in practice, a point where the decimal is a comma ("1.234.567,89") and a comma where the decimal is a point ("1,234,567.89"). The RAE's own recommendation is a thin space ("40 000"), which UI rarely follows.
  - No separator in years ("2026").
  - The RAE and CLDR "es" leave 4-digit numbers ungrouped ("1234"), but many apps group them. Be consistent.
- **Percent:** the RAE and es-ES use a space ("50 %"; use a non-breaking space). LatAm locales write "50%".
- **Currency:**
  - Spain: "12,99 €" (symbol after, with a space; ❌ "€12,99").
  - Latin America: symbol before, no space ("$249.00" Mexico, "$1.299" Argentina, "$12.990" Chile, "$25.900" Colombia).
  - "$" means pesos in MX, AR, CL, CO and UY. In cross-border contexts, use ISO codes: "USD 10", "MXN 250".
  - Currency names are lowercase ("pesos", "dólares"). Don't combine symbol and code (❌ "USD $10").
- **Dates:** day-month-year everywhere except Puerto Rico (month first).
  - Long form: "7 de marzo de 2026". With a weekday: "sábado, 7 de marzo".
  - Months and days are lowercase. No English ordinals ("7th"). "1 de mayo" or "1.º de mayo".
- **Time:** the separator is a colon ("15:30"), never a comma.
  - The 12-hour clock uses **a. m. / p. m.** (RAE: lowercase, periods, internal space). ❌ "3:30PM".
  - CLDR prints "p.m." for Mexico and US Spanish. Follow the platform formatter, but never write "AM/PM" in hand-written copy.
  - The 24-hour clock is standard in Spain and common in the Southern Cone. Don't combine "h" with a. m./p. m.
- **Units:** the symbol goes after a space, with no period and no plural "s": "15 km", "3 kg", "25 °C" (❌ "3 kgs.").
- **Abbreviations** take a period ("pág.", "núm."/"n.º", "máx."). Plural initialisms double their letters: "EE. UU.", "RR. HH."
- **In UI, use digits** for all numbers, including 1–9, for scannability.

---

## 14. Review checklist

- [ ] Locale and register (*tú*, *usted* or *vos*) decided and applied everywhere: verbs, pronouns, possessives, clitics.
- [ ] Buttons and menus use infinitives (or nouns), with no period and a specific verb. Dialog buttons repeat the title's verb. No Sí/No on destructive actions.
- [ ] Instructions use real imperatives, not infinitives aimed at the user.
- [ ] Sentence case everywhere. Days, months and languages lowercase. Capitals keep their tildes.
- [ ] Opening ¿ and ¡ present. No period after ? or !. No serial comma. Vocative comma present.
- [ ] All tildes and ñ correct. "solo" without a tilde. Plurals written out.
- [ ] Gender and number agreement checked for every variable and count. No concatenated fragments. No "(s)".
- [ ] No passive calques. The recent-past tense matches the locale.
- [ ] No false friends (salvar, resumir, soportar, librería, eventualmente…). Tech terms follow §8.
- [ ] Errors explain what happened and how to fix it, without blame. "Lo sentimos" only for real failures. "Por favor" not used as padding.
- [ ] Neutral wording in place of gendered adjectives addressed to the user. No @, x or e.
- [ ] Regional vocabulary is consistent with the target locale. No slang, no risky words (coger…).
- [ ] Numbers, currency, dates and times follow the locale. a. m./p. m. spelled correctly.
- [ ] Labels fit or wrap gracefully. No invented abbreviations.

---

## 15. Sources

- RAE, *Diccionario panhispánico de dudas*: https://www.rae.es/dpd/ (entries: signos de interrogación y exclamación, mayúsculas, punto, dos puntos, puntos suspensivos, gerundio, voseo, ordenador, nivel, eventualmente, remover, aplicar, chequear, email)
- RAE, *Ortografía de la lengua española* (2010): https://www.rae.es/ortografía/ (decimal and thousands separators, time expressions)
- RAE, *Libro de estilo de la lengua española* (2018), digital writing chapter: https://www.rae.es/libro-estilo-lengua-española/escritura-y-comunicación-digital
- RAE, *Español al día*: "Infinitivo por imperativo", "El adverbio solo y los pronombres demostrativos sin tilde", "Cuándo se usa cada tipo de comillas" — https://www.rae.es/espanol-al-dia/
- RAE, capital letters in titles: https://www.rae.es/buen-uso-español/las-mayúsculas-en-los-títulos
- RAE, inclusive-language report (2020) and summary: https://www.rae.es/sites/default/files/Informe_lenguaje_inclusivo.pdf
- FundéuRAE network: https://fundeu.do/ ; Fundéu Argentina, currencies: https://fundeu.fiile.org.ar/ ; FundéuBBVA touch-gesture glossary: https://www.bbva.com/es/dirias-espanol-correcto-scroll/
- Microsoft Localization Style Guides (Spanish: Spain, Mexico, Neutral, US): https://learn.microsoft.com/globalization/reference/microsoft-style-guides
- Google conversation design guidance, es-419: https://developers.google.com/assistant/conversation-design/language?hl=es-419
- Apple Support (es-ES and es-LA) and Google Help (es and es-419), compared for vocabulary and register
- UN, Spanish gender-inclusive language guidelines: https://www.un.org/es/gender-inclusive-language/guidelines.shtml
- Argentina.gob.ar content standards (voice, plain language, writing): https://www.argentina.gob.ar/contenidosdigitales
- Colombia DNP, *Guía de lenguaje claro para servidores públicos*
- W3C, Text size in translation: https://www.w3.org/International/articles/article-text-size
- Unicode CLDR locale data: https://cldr.unicode.org/