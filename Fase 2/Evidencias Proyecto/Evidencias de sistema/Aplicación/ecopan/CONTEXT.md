# EcoPan

Bakery management system: a till (POS), a baker's mobile client and a manager web dashboard.

## Language

**Ingredient inventory**:
Stock of the bakery's raw ingredients (flour, sugar), measured in weight and consumed by production through formulas. This is the only thing "Inventario" means.
_Avoid_: using "inventory" for finished products.

**Product stock**:
Units on hand of a finished product that is bought, not baked (soda, packaged cookies). Tracked on its own, separately from **Ingredient inventory**.
_Avoid_: "inventory", "resold stock".
Shown and edited on the **Almacén product** itself, not on a separate stock area.

**Formula**:
The ingredient recipe of a product the bakery makes (bread and pastries). Only **Panadería products** have one.

**Product type** (Spanish label: *Tipo*):
The single classification of a product, from a fixed list: *Pan*, *Pastelería*, *Almacén*. Not manager-extendable. Whether a product is baked or bought-in is read from it; there is no separate "origin" for a user to choose (the API still carries `origin`, derived from the type).

**Panadería product**:
A product whose type is *Pan* or *Pastelería*: the bakery bakes it. It has a **Formula** and goes through production batches. Sold by weight or by unit. Pan and Pastelería can be switched between after creation.

**Almacén product**:
A product whose type is *Almacén*: bought finished and sold as-is (soda, packaged cookies). No formula, no production; sold by unit and has **Product stock** (opening count, reorder level). Its type cannot be changed to Pan/Pastelería afterwards, nor the reverse.

**Stock states** (Almacén product or ingredient, per branch):
*Stock bajo* = above 0 and at or under the reorder level; *Agotado* = exactly 0; *Stock negativo* = below 0 (more used or sold than recorded; surfaced, never blocked). One rule for ingredients and Almacén products, the same on the till, the baker's client and the web.

**Sold by**:
How a product is priced at the till: by weight or by unit. Independent of whether the product is made or bought-in.

**Kg de masa** (dough weight):
What a production batch records: the weight of the dough mixed, not of the bread that comes out of the oven. Production figures are always labelled as dough.
_Avoid_: "kilos produced" or "bread produced" without saying it is dough.

**Reconciled inventory report**:
For a date range, per ingredient: the stock at the start, plus purchases, minus formula decrements, plus or minus manual adjustments, equals the stock at the end. "Reconciled" means this ledger balances; it does not compare against a physical count or against expected usage from sales.

**Baseline forecast** (*Línea base*):
A forecast for a **Panadería product** in a branch that has too little sales history for the model (or that the model is not trusted on); it is a simple average of recent days, labelled as such wherever it is shown, never presented as the model's.
_Avoid_: calling every forecast "the model's prediction".

**Producción sugerida** (suggested production):
The expected sales of a **Panadería product** for the next day at one branch, in the unit it is sold by. It is a forecast of sales, not a quantity of dough and not a batch; the baker decides what to bake. **Almacén products** have none.
_Avoid_: calling it a production order or a recipe quantity.
