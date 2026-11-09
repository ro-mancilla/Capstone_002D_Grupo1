# RESULTS — demand forecasting

The defence artifact (plan 08 §7). Everything quantitative about the ML component lives here.

> **STATUS: run end to end on Favorita. Every number below is measured, none is illustrative.**
> The test window was scored **three times** — §4.1 and §4.1b give the reason for each departure
> from the single-scoring rule, both written before the scoring they describe. Because a thrice-
> revised number should not be load-bearing alone, the claim in §4.4 is a **range over five
> evaluation windows**, of which the holdout is one. Reproduce with the commands in §6.

---

## 1. The evaluation-validity argument

*The paragraph the whole ML component stands on. Read this first.*

The reference client is a bakery that has never recorded per-product-type daily sales — it has **no
history at all**. That is not merely inconvenient; it makes an honest accuracy claim impossible from
client data, because there is no legitimate holdout to measure against. Inventing data and scoring
on it would be circular: a model that recovers the seasonality we programmed into a generator has
demonstrated nothing.

So model quality is proven on **Corporación Favorita** (Kaggle, 2013–2017): real Ecuadorian grocery
retail at daily × store × family grain, with real holidays, real promotions and real seasonality,
including a `BREAD/BAKERY` family that is literally the category we forecast. Favorita's
(store, family) maps onto our (branch, product_type), and aggregating items up to family puts the
series at exactly the grain a bakery plans at. The model is scored against two deliberately strong
naive baselines under rolling-origin evaluation, with the success threshold committed to git
*before* any model was tuned. The pre-registered holdout was scored three times rather than once
(§4.1, §4.1b give the reason for each, both written beforehand), so the claim does not rest on it
alone: **§4.4 reports five evaluation windows as a range, +36.7% to +45.4%**, of which the holdout
is one pre-registered row.

Synthetic data appears in this project in exactly one place — the live demo — where it exercises
plumbing (sale → sync → forecast → dashboard) and no accuracy claim is attached to it. A test
enforces that the demo path and the evaluation path never touch.

Cold start is a deployment story, not an evaluation problem, and it is answered honestly: a series
with under 28 days of history is served by a labelled baseline (`baseline-swlw`), not by the model,
and the dashboard can say so. **The same is true of any series the model has not been trained on,
whatever its history length — which today means every bakery series.** The pre-registered transfer
experiment (§4.7) failed its bar: on unseen, gram-rescaled series the model's WAPE was 0.975 against
the baseline's 0.192. The +36.7% … +45.4% claim above is therefore a claim about Favorita-like series
the model was trained on, **not** about what the bakery receives: **the bakery is served by the
labelled baseline until it has its own training data**, and the service enforces this by routing
unseen series to the baseline. The system's first contribution to this bakery is that it *starts
recording* the data nobody was recording; the model is what that record later makes possible.

**The honest limits.** Favorita is a supermarket chain, not a bakery: per-series volumes are larger,
and same-day perishability has no analogue in the data. The claim is therefore "this model learns
the structure of daily retail demand of the kind the client has", not "these exact error rates will
hold at the client".

### Censored demand: what is being forecast is sales, not demand

Every number in this document is fitted to and scored against **units sold**. Demand is what a
bakery actually wants, and the two differ whenever a product sells out: the shelf empties at 11am,
six more customers ask and leave, and the day records the shelf's capacity rather than the town's
appetite. Sales are demand *censored from above* by supply, and a model trained on censored data
learns the ceiling along with the signal.

The direction of the resulting error is predictable and one-sided. Sell-out days are
under-recorded, so the model forecasts low, so the bakery bakes less, so it sells out again — a
feedback loop that converges on the ceiling instead of on the demand. It is the standard failure of
naive replenishment models and it is worth naming before someone else does.

Three things keep it from undermining the results here:

1. **It does not bite on this dataset.** Favorita is grocery retail with continuous replenishment;
   a store restocks bread through the day rather than baking one batch at dawn. Stockouts exist but
   are not the dominant regime, and — decisively — **the censoring is identical for the model and
   for both baselines**, because all three are fitted and scored on the same recorded sales. A
   relative gain measured under shared censoring is still a valid relative gain. The absolute WAPE
   would move if true demand were observable; the +39% would not, except to the extent censoring
   is correlated with the patterns the model exploits, and there is no reason to expect that here.

2. **The product collects the ingredients of the fix.** The production module records what was
   baked and the POS records what was sold, so `batches_logged − units_sold` is observable per
   (branch, product type, day). A day that ends with zero leftovers **and** a same-day production
   quantity at or below forecast is a censoring candidate. That signal does not exist in Favorita,
   which is exactly why this is an argument rather than an experiment. (Edited 2026-09-12: an
   earlier version of this paragraph also cited a per-line day-old flag on sales; that flag was
   removed with the rest of the day-old discount path, and the leftover figure is meant to come
   from an end-of-day closing count instead.)

3. **The correction is a known, bounded piece of work, not a research project.** Once the client
   has stockout flags the standard treatment is Tobit-style censored regression, or the simpler
   operational version: treat a sold-out day's observation as a *lower bound* and either drop it
   from the loss or impute it from comparable uncensored days. Neither changes the feature
   pipeline or the serving contract.

**What this means for the claim.** Under censoring the reported gain is, if anything, conservative
in operational terms: a model that beats the baseline on recorded sales will beat it on true demand
by at least as much, because censoring truncates the peaks the baseline is worst at. That is an
argument, not a measurement, and it is offered as one — deliberately not run as an experiment on
Favorita, where a "stockout" would have to be invented from a threshold rule and the resulting
number would measure the rule rather than the world.

### Data provenance — read this, it is a caveat on everything below

The data did **not** come from the Kaggle competition endpoint. It came from a third-party
re-upload: the Kaggle dataset `ruiyuanfan/corporacin-favorita-grocery-sales-forecasting`.

Why: the competition's download API returns `403 Forbidden` for this project's Kaggle account
(`userHasEntered: False`). Both authentication methods and both official clients (`kaggle` CLI and
`kagglehub`) were tried and all four combinations fail identically, with the API's own message
naming un-accepted competition rules as the cause. The competition closed in January 2018 and may
no longer admit new entrants.

Why the re-upload is nonetheless trustworthy, and how that was checked rather than assumed:

- **Two independent re-uploads report byte-identical sizes** for every table
  (`train.csv` 4,997,452,288; `items.csv` 101,841; `stores.csv` 1,387; `holidays_events.csv`
  22,309). Two unrelated uploaders do not independently produce identical byte counts from a
  modified dataset.
- The downloaded file matches those sizes exactly, and its contents are structurally what the
  competition documentation describes: 125,497,040 rows, `2013-01-01 … 2017-08-15`, 54 stores in
  Ecuadorian cities, 33 families including `BREAD/BAKERY`, `onpromotion` null for the early period,
  Spanish-language holiday descriptions.

**Licensing is unchanged by the route taken.** The data remains governed by the Corporación
Favorita competition rules; a re-uploader cannot grant terms they do not hold, and the Kaggle
dataset page lists its licence as "unknown". Nothing derived from it is redistributed here — only
metrics and plots, which is safe under any plausible reading. If the competition account is ever
sorted out, re-running `make data` against the official endpoint should reproduce these numbers
exactly; that is the check that would settle it.

---

## 2. Dataset scope and splits

| | |
|---|---|
| Dataset | Corporación Favorita — via mirror, see *Data provenance* above |
| Grain | daily × store_nbr × family |
| Raw input | 125,497,040 rows (`train.csv`, 4.997 GB) |
| Scope used | **full** — all 54 stores, all 33 families, no row cap |
| After aggregation | 2,436,459 daily rows · 1,729 series (store × family) · 2013-01-01 … 2017-08-15 · total demand 1,073,753,856 |
| Train | ≤ 2017-05-31 |
| Validation | 2017-06-01 … 2017-06-30 (~4 weeks) |
| Test (held out; scored 3×, see §4.1/§4.1b) | 2017-07-01 … 2017-08-15 (~6.5 weeks) |
| Protocol | rolling origin, horizon 7, step 7 (non-overlapping) |

Preprocessing decisions that move the numbers, stated rather than buried:
- Negative `unit_sales` (returns) clipped to 0 — a return is not negative demand.
- Absent days densified to 0, per the competition convention. This conflates "closed" with "open,
  sold nothing", which biases slightly toward under-forecasting around closures.
- Transferred holidays dropped at their nominal date and counted at their observed date;
  `Work Day` rows excluded from the holiday flag.
- `use_promo` disabled: safe only when the promo calendar is known across the horizon at serving
  time, which the current worker does not supply.

---

## 3. Threshold record (committed BEFORE any model tuning)

Plan 08 §3's anti-target-shopping procedure. The point of writing this down and committing it is
that the git timestamp proves the bar was set before any model number existed.

**Procedure**
1. Run both baselines through the harness on the **validation** window.
2. The better one by pooled WAPE becomes the **reference baseline**.
3. Record the threshold here and commit — *before* the first `make train`.
4. The test window is scored once, after tuning freezes. (Departed from twice — a bug fix, §4.1,
   and a hyperparameter correction, §4.1b — each disclosed and pre-registered before running.)

**Bar:** the model must beat the reference baseline by **≥ 10%** on **both** pooled MAE and pooled
WAPE, on the untouched test window.

Both metrics, not either: a model can buy MAE by shading predictions toward the median while making
the volume-weighted picture worse, and either metric alone is gameable in a way the pair is not.
10% is the low end of the 10–15% typical for a GBM over a same-weekday naive on daily retail —
chosen at the low end deliberately, because a bar set where we expect to land is not a bar.

Scored on the validation window (2017-06-01 … 2017-06-30), rolling origin, horizon 7, step 7,
n = 47,203 pooled (series × forecast day).

| | MAE | RMSE | WAPE |
|---|---|---|---|
| `baseline-swlw` (same weekday last week) | **95.61** | **365.55** | **0.1861** |
| `baseline-ma7` (7-day moving average) | 129.53 | 467.25 | 0.2521 |

**Reference baseline: `baseline-swlw`** — best pooled WAPE on validation.

`baseline-swlw` wins on every metric and by a wide margin — 26% lower MAE than the moving average.
That is the expected ordering and worth stating plainly: daily grocery demand is strongly
weekly-periodic, so copying last week's same weekday is a genuinely hard reference, while a 7-day
mean smooths away exactly the weekday structure that dominates the series. **This is not a
straw-man baseline**, which is the point of choosing it.

**Bar the model must clear on the untouched test window** (unchanged from the procedure above,
≥ 10% relative on both):

| | Reference | Required |
|---|---|---|
| Pooled MAE | 95.61 | **≤ 86.05** |
| Pooled WAPE | 0.1861 | **≤ 0.1675** |

**Audit trail, stated precisely.** `min_relative_improvement: 0.10` was committed in `893c6d1`
(2026-07-25), before the dataset was even obtained — so the *bar* has genuine git-timestamp
evidence predating any model number. `reference_baseline: baseline-swlw` was set the following day
from the validation run above, and was chosen by the harness (best pooled WAPE), not by hand. The
threshold table above was written before `make train` was invoked, though it was not committed at
that instant; the strong claim is the one about the 10% bar, and that one holds.

---

## 4. Test-window results

Window 2017-07-01 … 2017-08-15. Rolling origin, horizon 7, step 7, n = 69,622 pooled.

### 4.1 The second scoring — written before it was run

Plan 08 §3 says the test window is scored once. It was scored a second time, and pretending
otherwise would be worse than the second scoring is. What happened, and the commitments made
*before* re-running:

**The defect.** `models/predict.py` hard-coded `is_holiday` and `is_day_before_holiday` to `0` for
every forecast date, and `pipeline.py`'s `cmd_evaluate` used that same `recursive_forecast` to score
the test window. The model is *trained* on the real flags, so it was being evaluated — and served —
with two of its fifteen features switched off. **12 of the 46 scored dates (26%)** carry one of
those flags, so this is not a rounding error.

**The model artifact is unchanged.** The bug was confined to the prediction path; training always
saw the true flags. The re-score therefore reuses the byte-identical `model.txt`, which isolates
exactly one variable: the flags reaching the model at inference. No hyperparameter was touched, and
the ≥10% threshold from §3 is unchanged.

**A bug fix is not tuning — but only if the direction is pre-committed.** Re-scoring after seeing a
result is the target-shopping plan 08 §3 exists to prevent, so before running the second evaluation
the outcome was checked on the **validation** window (already spent on early stopping, so free) and
recorded here:

| pooled, validation window | MAE | RMSE | WAPE |
|---|---|---|---|
| bugged path (flags zeroed) | 60.163 | 248.886 | 0.11709 |
| fixed path (real calendar) | 60.610 | 249.888 | 0.11796 |

**The fix makes the model very slightly worse — about 0.7% relative — not better.** That was known
before the test window was re-scored, and the second scoring was run anyway, because a serving path
that disagrees with the training path is a defect whichever way the metric happens to move. The
commitment made at this point was to publish the new test number in place of the old one regardless
of direction. §4.2 is the result of that commitment.

The pre-fix numbers are preserved in `reports/evaluation/pre_holiday_fix_test_evaluation.json`
(raw prediction frames stay out of git: they carry Favorita `y_true`, which is not ours to
redistribute).

### 4.1b The third scoring — and why it is a weaker excuse than the second

*Written and committed before the third `make evaluate` was run.*

Earlier versions of this file carried a caveat: *"training stopped at iteration 2999 of a 3000 cap;
early stopping never fired and validation loss was still falling, so this result is a floor."* That
sentence appeared twice without a number behind it. It was an untested assumption wearing a
caveat's clothing, and the honest move is to test it rather than repeat it.

Tested. The recorded learning curve showed no plateau — validation L1 fell 3.0% over the final
1,000 iterations and was still descending at ~1.45 per 1,000 at the cap. So the cap, not early
stopping, chose the model, which means the config's own selection mechanism never ran.

Re-running with `num_boost_round: 20000, early_stopping_rounds: 200`, **early stopping fired at
iteration 3,875**. Measured cost of the old cap:

| | best iteration | validation MAE |
|---|---|---|
| capped run (`num_boost_round: 3000`) | 2,999 of 3,000 | 58.118 |
| converged run (`num_boost_round: 20000`) | **3,875** of 20,000 | **57.204** |

**The cap was 876 rounds short, worth 1.57% of validation MAE.** The "floor" claim was directionally
right and smaller than the extrapolation suggested — the curve decelerates.

**Be clear about what kind of departure this is.** The second scoring was forced by a *defect*: the
serving path disagreed with the training path. This third one is a **hyperparameter correction**,
which is closer to tuning, and tuning against a holdout is exactly what the single-scoring rule
exists to prevent. Calling it a bug fix would be a stretch. The mitigation is not a better excuse
but a change of claim:

> **The headline moves off the single window.** §4.4 reports all five evaluation windows — the four
> walk-forward folds and the pre-registered holdout — as one range. The holdout becomes one
> pre-registered row among five rather than the entire case. A number that has been revised three
> times should not be load-bearing on its own, and now it isn't.

Pre-committed before running: the new figure is published in place of the old one **whichever
direction it moves**, including if the converged model scores worse. Pre-convergence artifacts are
preserved under `data/artifacts/pre-convergence/` with derived metrics in `reports/evaluation/`.

### 4.2 Scored results (third scoring, converged model)

| Estimator | MAE | RMSE | WAPE | Δ MAE vs ref | Δ WAPE vs ref |
|---|---|---|---|---|---|
| `baseline-swlw` *(reference)* | 95.30 | 345.15 | 0.1830 | — | — |
| `baseline-ma7` | 124.87 | 431.04 | 0.2397 | −31.0% | −31.0% |
| **`lgbm-favorita`** | **58.18** | **212.84** | **0.1117** | **+39.0%** | **+39.0%** |

**Verdict: PASS** — MAE +39.0%, WAPE +39.0%, against a bar of ≥ 10% on both. RMSE +38.3%, which
matters because RMSE is dominated by the worst days: the model is not buying its average by giving
up on the spikes.

All three scorings of this window, so the revisions are visible rather than described:

| # | Model | Reason for the scoring | MAE | WAPE | Gain |
|---|---|---|---|---|---|
| 1 | 3,000-round cap, holiday flags zeroed | original | 59.11 | 0.1135 | +38.0% |
| 2 | 3,000-round cap, holiday flags fixed | defect (§4.1) | 59.17 | 0.1136 | +37.9% |
| 3 | **converged (3,875), flags fixed** | hyperparameter correction (§4.1b) | **58.18** | **0.1117** | **+39.0%** |

The total movement across three scorings is **1.1 percentage points**, and the verdict, the
threshold and every qualitative conclusion held throughout. That stability is the point worth
making: the result was never balanced on the edge of the bar.

**The pooled figure is not the operational one.** It averages horizons 1–7. The decision this
system supports is "decide tonight what to bake tomorrow" — d+1, where the gain is **+42.8%**
(WAPE 17.7% → 10.1%). Weekly planning is a much weaker claim; see the horizon table.

### Bread/bakery family breakdown

The flagship series — closest analogue to the client's product mix. 54 series, n = 2,268 scored
days, mean 550 units/day. Holdout window, horizons 1–7 pooled.

| | MAE | RMSE | WAPE |
|---|---|---|---|
| `baseline-swlw` (seasonal naive) | 72.10 | 103.93 | 0.1310 |
| `baseline-ma7` (the plain average) | 101.18 | 152.18 | 0.1838 |
| **`lgbm-favorita`** | **51.96** | **75.91** | **0.0944** |

**+27.9% against the seasonal-naive reference** (13.1% error down to 9.4%) and **+48.6% against the
plain 7-day average** — the latter being the comparison most people mean when they ask whether a
model beats "just taking an average". It roughly halves that error.

Note the bakery gain is *smaller* than the all-family gain (+27.9% vs +39.0%), and that is the
honest headline for this project rather than a disappointment: bakery demand is the most
weekly-periodic category in the dataset, so the same-weekday rule is at its strongest exactly here
and there is least for a model to add. Quoting the all-family number as though it were the bakery
number would overstate the case by 11 points.

### Error by horizon

Recursive multi-step forecasting compounds error: day 7 feeds on six of its own predictions.
Reported rather than averaged away, because it tells the bakery Monday's number is worth more than
next Sunday's.

| Horizon | Baseline MAE | Model MAE | Baseline WAPE | Model WAPE | Model gain |
|---|---|---|---|---|---|
| d+1 | 108.0 | 61.8 | 17.7% | 10.1% | **42.8%** |
| d+2 | 112.7 | 74.7 | 16.8% | 11.1% | 33.7% |
| d+3 | 88.8 | 53.6 | 17.5% | 10.6% | 39.6% |
| d+4 | 110.8 | 51.3 | 23.4% | 10.8% | **53.7%** |
| d+5 | 95.8 | 48.7 | 19.8% | 10.1% | 49.2% |
| d+6 | 76.3 | 52.1 | 18.7% | 12.7% | 31.7% |
| d+7 | 74.6 | 65.0 | 15.3% | 13.3% | **12.8%** |

The shape of that last column is the operationally important finding, and it is not what the pooled
number suggests. The advantage is large through d+5 and then collapses: by d+7 it only just clears
the bar. That is recursive forecasting doing what it does — day 7 is built on six of the model's own
predictions, so errors compound, while the naive rule's error does *not* grow with horizon (it
always copies one real observation).

Practical consequence: **this model earns its place on "what do I bake tomorrow", not on "plan my
whole week".** A weekly-planning feature on d+7 numbers runs on a ~13% edge over a SQL query; a
next-morning baking suggestion runs on a ~43% edge.

---

## 4.3 Does the gain survive a different six weeks? (walk-forward backtest)

`make backtest` · results in `reports/evaluation/backtest.json`

§4.2 is one six-week window. It contains seven rolling origins, but all seven sit at the same
position in the calendar, so it cannot answer the obvious objection: *would this hold in December?*
A holiday cluster landing on one side of a single split can move WAPE a long way, and "scored once"
is a virtue for honesty and a weakness for generalisation at the same time.

Four folds at different seasonal positions, each with the model **retrained from scratch** on data
strictly before that fold. Retraining is not optional — the shipped artifact was trained on data
that includes all four windows, so scoring them with it would be leakage in the flattering
direction. Every fold scores before 2017-07-01 and `cmd_backtest` refuses a window that reaches the
holdout: a robustness study that spends the holdout to prove the holdout was fine is not one.

| Fold (6 weeks) | Rounds chosen | Baseline WAPE | Model WAPE | Gain (d1–7) | Gain (d+1) |
|---|---|---|---|---|---|
| 2016-09-01 … 10-12 (spring, no holidays) | 14,812 | 0.1844 | 0.1007 | **+45.4%** | +43.1% |
| 2016-12-01 … 2017-01-11 (Christmas/New Year) | 4,521 | 0.3409 | 0.2158 | **+36.7%** | +51.8% |
| 2017-03-01 … 04-11 (post-holiday, Carnival) | 7,919 | 0.2049 | 0.1283 | **+37.4%** | +46.0% |
| 2017-05-01 … 06-11 (immediately pre-holdout) | 12,037 | 0.2127 | 0.1248 | **+41.3%** | +49.9% |

### The 3,000-round cap was costing far more here than on the main run

The folds were re-run converged (§4.1b). Against the earlier capped run, the same four folds:

| Fold | Capped gain | Converged gain | Δ |
|---|---|---|---|
| 2016-09 | +30.9% | +45.4% | +14.5 pts |
| 2016-12 | +34.5% | +36.7% | +2.2 pts |
| 2017-03 | +34.0% | +37.4% | +3.4 pts |
| 2017-05 | +33.6% | +41.3% | +7.7 pts |

Every fold wanted **more** than 3,000 rounds — between 4,521 and 14,812 — so the cap was binding on
all of them, in one case costing 14 points of gain. This is the strongest evidence that the old
"under-trained" caveat was real rather than rhetorical.

**An anomaly to be upfront about:** the main run stopped at 3,875 while three of four folds ran past
7,900 under identical settings. The folds train on *less* data, so the naive expectation is the
opposite. The likely explanation is the validation window each run early-stops on — June 2017
flattens sooner than the folds' own months do — which means the shipped model may still be
short of where the fold evidence suggests it could go. It is reported as-is rather than
re-tuned: hunting for a validation window that lets the model train longer would be target-shopping
by another route.

---

## 4.4 The claim: a range over five windows

This is the number to defend. Not §4.2 alone — that figure has now been revised three times, and a
thrice-revised single measurement should not carry the argument by itself. Five evaluation windows,
all from the same converged model class, each scored against **its own** baseline:

| Window | Pre-registered? | Baseline WAPE | Model WAPE | Gain (d1–7) | Gain (d+1) |
|---|---|---|---|---|---|
| 2016-09 … 10 | no | 0.1844 | 0.1007 | +45.4% | +43.1% |
| 2016-12 … 01 | no | 0.3409 | 0.2158 | +36.7% | +51.8% |
| 2017-03 … 04 | no | 0.2049 | 0.1283 | +37.4% | +46.0% |
| 2017-05 … 06 | no | 0.2127 | 0.1248 | +41.3% | +49.9% |
| **2017-07 … 08** | **yes — the holdout** | 0.1830 | 0.1117 | **+39.0%** | **+42.8%** |

**Pooled horizons 1–7: min +36.7%, median +39.0%, max +45.4%.**
**Next-day (d+1) only: min +42.8%, median +46.0%, max +51.8%.**

Defend the minimum. The weakest window in the weakest framing still beats the seasonal-naive rule by
**more than a third**, against a pre-registered bar of 10%.

Two things changed when the folds were re-measured converged, and both reverse claims made in
earlier drafts of this document:

- **The holdout is no longer the optimistic outlier — it is now among the most conservative.**
  Earlier, with capped models, the holdout's +37.9% sat above every fold and the honest note was
  that the headline window had been favourable. Converged, +39.0% is the *median* of five, and on
  d+1 the holdout's +42.8% is the **minimum**. The pre-registered window turns out to be, if
  anything, a slightly hard one.
- **"December is hardest and the model does best there" no longer holds as stated.** December is
  still comfortably the hardest window — baseline WAPE 0.341 against 0.18–0.21 elsewhere — but
  converged, it has the *lowest* pooled gain of the five (+36.7%), not the highest. What survives is
  a narrower and more interesting claim: on the **next-day** horizon that actually drives the baking
  decision, December is the model's second-best window (+51.8%). So the model does not collapse when
  demand gets hard to predict; its advantage simply concentrates into the short horizon, which is
  the horizon the product uses.

### Reading the numbers

Every percentage in this document is a gain over a naive baseline, but they are not interchangeable.
What each one holds fixed:

| Figure | Window(s) | Horizons | Model | Baseline |
|---|---|---|---|---|
| **+39.0%** pooled gain | holdout only | 1–7 pooled | one fit | `baseline-swlw` |
| **+42.8%** next-day gain | holdout only | d+1 only | one fit | `baseline-swlw` |
| **+36.7 … +45.4%** range | 5 windows | 1–7 pooled | refit per window | that window's `baseline-swlw` |
| **+42.8 … +51.8%** next-day range | 5 windows | d+1 only | refit per window | that window's `baseline-swlw` |
| **+27.9%** bakery gain | holdout, `BREAD/BAKERY` | 1–7 pooled | one fit | `baseline-swlw` |
| **+48.6%** bakery vs average | holdout, `BREAD/BAKERY` | 1–7 pooled | one fit | `baseline-ma7` |

The absolute WAPEs across windows are **not** comparable to one another — different training cuts,
different amounts of available history, genuinely different demand volatility (December is harder
for everyone). Only the per-window gain over that same window's baseline is the claim.

---

## 4.5 Which features carry the gain? (ablation)

`make ablation` → `reports/evaluation/ablation.json`. Each variant is a `FeatureConfig` with one
group switched off, **retrained from scratch with early stopping** — reusing the full model's
iteration count would confound "this feature mattered" with "this variant was stopped in the wrong
place".

**Scored on validation, never on the test window.** An ablation asks which variant is best, which
makes it model selection; answering it against the held-out window would spend the pre-registered
claim on feature selection — the exact thing §4.1b exists to prevent. These numbers are therefore
*not* comparable to §4.2's.

The `full` row reproduces the shipped model exactly (MAE 57.204 at iteration 3,875), which is the
check that the harness and the shipped run agree.

| Variant | Features | Best iter | Validation MAE | Cost of removal |
|---|---|---|---|---|
| `full` | 15 | 3,875 | **57.204** | — |
| `no_holidays` | 13 | 5,535 | 56.971 | **−0.41%** |
| `no_weekly_lags` (drop lag_7/14/28) | 12 | 5,506 | 58.563 | +2.38% |
| `no_rolling` (drop rolling mean/std) | 12 | 2,220 | 58.894 | +2.95% |
| `no_calendar` (drop dow/month/dom/weekend) | 11 | 3,866 | 68.751 | **+20.19%** |
| `lag1_only` (yesterday, nothing else) | 9 | 2,878 | 68.923 | **+20.48%** |

Positive = removing the group made validation error worse, i.e. it was pulling weight.

**Calendar features are the model.** Dropping the four date-derived columns costs 20.2% of
validation MAE — nearly as much as stripping the model back to a single lag (20.5%). Day-of-week is
where the weekly rhythm lives.

**The weekly lags are almost redundant with them.** Dropping `lag_7/14/28` costs only 2.4%, because
`dow` already encodes the same rhythm in a form the trees split on more cheaply. The two encodings
overlap heavily, and it is the calendar side that is load-bearing — not the intuition one would
have from a feature-importance chart, where the lags dominate.

**The holiday flags do not earn their place — and it is worth saying so.** Removing them *improved*
validation MAE by 0.41%. The honest reading is "no measurable contribution", not "actively harmful":
0.41% is small and the variant early-stopped 1,660 iterations later, so some of the difference is a
different stopping point rather than a better model.

That is an uncomfortable result to publish given the holiday train/serve bug disclosed in §4.1, and
it is published anyway. It does not invalidate the fix: the bug made training and serving disagree,
and a feature that contributes nothing must still be *consistent* across the two paths or its noise
is fed to the model differently in production than in training. The feature stays for now — Chilean
bakery demand around Fiestas Patrias and Christmas is a much stronger holiday signal than
Ecuadorian grocery data shows, and dropping it on this evidence would optimise for the proxy
dataset rather than the client.

## 4.6 How much history does a series need? (cold-start curve)

`make coldstart` → `reports/evaluation/coldstart_curve.json`. The shipped model, no retraining, with
each origin's history truncated to the last *N* days — the position a newly-onboarded bakery is
actually in. Validation window, because this chooses a deployment parameter.

| History | Model WAPE | Cold-start WAPE | Model advantage |
|---|---|---|---|
| 7 d | 0.2096 | 0.1861 | **−12.7%** (worse) |
| 14 d | 0.1408 | 0.1861 | +24.3% |
| 21 d | 0.1408 | 0.1861 | +24.3% |
| 28 d | 0.1163 | 0.1861 | **+37.5%** |
| 35 d | 0.1163 | 0.1861 | +37.5% |
| 42 d | 0.1163 | 0.1861 | +37.5% |
| 56 / 90 / 180 d | 0.1163 | 0.1861 | +37.5% |

Three readings, in order of consequence:

1. **At 7 days the model is worse than the baseline**, by 12.7%. Every weekly and monthly feature is
   NaN, and a pooled model extrapolating from missing features loses to "same weekday last week".
   The cold-start fallback is not defensive boilerplate — it is load-bearing.
2. **The curve is flat from 28 days on**, identical to four decimal places at 35, 42, 56, 90 and
   180. 28 is exactly `FeatureConfig.max_lookback`: the point where the longest lag and the longest
   rolling window are fully populated. Beyond it, more history adds literally nothing.
3. **`MIN_HISTORY_FOR_MODEL` was 42 and is now 28.** The old value was 28 plus "two weeks of margin
   for the rolling windows to be meaningful" — a reasonable-sounding assumption that this measures
   and finds empty. It withheld the model from a new series for two weeks longer than the data
   supports. The constant is now *derived* (`FeatureConfig().max_lookback`), so changing `lags` or
   `rolling_windows` moves it instead of leaving a stale number behind.

The caveat that keeps this honest: Favorita series are denser and higher-volume than one bakery's.
If the client's first weeks are noisy enough to justify margin, this is the knob — but it should be
moved by a measurement, not by a feeling, which is the whole point of having the curve.

## 4.7 Does the gain transfer to unseen, rescaled series? (pre-registered — written BEFORE the run)

*Committed before `make transfer` exists or has been run. The git timestamp of this section is the
evidence; the result, whatever it is, is appended below the line marked RESULT once it exists.*

**The gap this closes.** The +39% claim (§4.4) is measured on Favorita series the model was trained
on: known `store_nbr` / `family` codes, unit counts. At the bakery every series is **unseen** — a
branch UUID and a product id that encode to −1 (LightGBM "missing") — and by-weight products are
counted in **grams**, orders of magnitude above Favorita's units. §4.6 only truncated the history of
*known* series. Whether the gain survives the bakery's actual situation is unmeasured; this section
measures it.

**The bar.** On the held-out unseen/rescaled series (arm D below), the shipped model must beat
`baseline-swlw` by **≥ 10% on BOTH pooled MAE and pooled WAPE** (horizons 1–7 pooled). Same 10% and
same two-metric rule as the §3 threshold; no new freedom is introduced.

**Protocol (fixed here, not tunable after the run).**

1. *Window.* **Validation only** (2017-06-01 … 2017-06-30), rolling origin, horizon 7, step 7 — the
   same harness (`evaluate_estimator`) and the same origins as §4.6. The test window is not touched:
   this chooses a deployment rule, so it must not spend the holdout.
2. *Model.* The **shipped** artifact (`data/artifacts`, `lgbm-favorita`), no retraining.
3. *Held-out series.* 10 of the 54 stores, drawn once with `numpy.random.default_rng(50)`
   (`choice` over the sorted store numbers, without replacement); all 33 families of those stores
   (330 series). The seed and the draw are fixed now.
4. *Unseen.* For these series both categorical keys (`store_nbr`, `family`) are replaced by values
   absent from the encoding, so they encode to −1 — exactly what a branch UUID / product id does at
   serving.
5. *Rescale.* Demand (history and target) is multiplied by **500**: one Favorita unit read as a
   500 g loaf. This is a stated, arbitrary gram-like factor, not a calibrated one; WAPE is
   scale-free by construction, so the rescale can move MAE gains only through the model, which is the
   thing under test.
6. *Arms.* Same series, same origins, four arms, so a failure can be located:
   A control (seen codes, ×1) · B unseen codes only (×1) · C rescaled only (×500, seen codes) ·
   **D unseen + ×500 (the binding arm)**. Only D carries the bar; A–C are diagnostic.
7. *Also reported, not part of the bar:* the d+1 gain of each arm, separately from the pooled
   horizons.

**Consequence.**

- **Pass (D clears 10% on both MAE and WAPE):** unseen series keep model routing above
  `MIN_HISTORY_FOR_MODEL`; §1 may say the bakery is served by the model from day 28.
- **Fail (D misses on either):** the service routes any series whose branch or product key is not in
  the artifact's encoding to the labelled baseline **regardless of history length**, and §1 and the
  defence state that the bakery is served by the baseline until it has its own training data. The
  number is published either way.

**Known limits, stated in advance.** (i) The shipped model *was trained* on these stores' history
(≤ 2017-05-31): masking their codes removes identity, not the level the trees may have memorised
through the lag features, so D is optimistic about true transfer to a never-seen business. (ii)
Validation also chose the model's stopping iteration, so it is not independent of the model — the
price of not spending the test window. (iii) Favorita grocery is not a bakery; a pass licenses model
routing, it does not prove the bakery's WAPE.

### RESULT (`make transfer` → `reports/evaluation/transfer.json`, appended after the run)

Run once, as pre-registered: validation window 2017-06-01 … 06-30, shipped model `lgbm-favorita`,
stores 4, 6, 9, 20, 34, 41, 42, 46, 49, 53 (seed 50). One deviation from the text above: those
stores hold **325** series, not 330 — a few (store, family) pairs have no sales rows — and the
protocol said "all families of those stores", which is what was done.

| Arm | Keys | Scale | Baseline MAE | Model MAE | Baseline WAPE | Model WAPE | Gain (MAE = WAPE) | d+1 gain |
|---|---|---|---|---|---|---|---|---|
| A control | seen | ×1 | 113.0 | 67.1 | 0.1920 | 0.1140 | **+40.6%** | +43.1% |
| B unseen codes | unseen | ×1 | 113.0 | 93.0 | 0.1920 | 0.1580 | **+17.7%** | +30.1% |
| C rescaled | seen | ×500 | 56,507 | 287,225 | 0.1920 | 0.9758 | **−408.3%** | −392.9% |
| **D unseen + rescaled (binding)** | unseen | ×500 | 56,507 | 287,089 | 0.1920 | 0.9753 | **−408.1%** | −392.7% |

**Verdict: FAIL.** Arm D misses the ≥ 10% bar on both MAE and WAPE, by a very wide margin: on
gram-scale demand the model is about five times *worse* than "same weekday last week". (MAE and WAPE
gains coincide because both share one denominator, the summed actuals, within each arm.)

What the arms say, in order of consequence:

1. **The damage is the scale, not the identity.** Arm B (unseen keys, unit scale) still beats the
   baseline by +17.7% — degraded from +40.6%, and the d+1 gain of +30.1% survives — so losing the
   store/family identity costs about 23 points but does not break the model. Arm C (known keys,
   ×500) is as bad as D. A tree ensemble cannot extrapolate: every lag and rolling mean sits far
   above any split threshold seen in training, so the predictions saturate near Favorita's largest
   unit-scale leaves and the forecast stops tracking the series.
2. **This is the stated factor, not a measured one.** ×500 is arbitrary and gram-like; the failure
   is gross enough that it will not hinge on 500 vs 250 or 1000, but the experiment does not say
   *where* between ×1 and ×500 the model breaks, and was not designed to.
3. **A and B are the optimistic side.** The model trained on these stores, so B overstates transfer
   to a never-seen business (limit (i) above). Even so, the binding arm failed.

**Consequence applied.** `ROUTE_UNSEEN_TO_BASELINE = True` in `service/schemas.py`: any series whose
`branch_id` or `product_type` is absent from the artifact's `encoding.json` is served by the labelled
baseline regardless of history length (`service/app.py`, covered by `tests/test_unseen_routing.py`).
§1 states the consequence for the bakery. The bakery is therefore served by `baseline-swlw` (and the
28-day-gated model path is dormant for it) until it has training data of its own.

What would change this: a model that is scale-aware (per-series normalisation of lags and target, or
predicting a ratio to a trailing mean), retrained and re-scored under a *new* pre-registration. That
is not done here; this section records only that the shipped model, as trained, does not transfer.

**Post-hoc clarification (added after the run; no number changed).** Protocol item 3 says the draw
is over "the sorted store numbers". The run drew over the *string*-sorted store keys (the processed
data stores `store_nbr` as text), i.e. the order `1, 10, 11, …, 2, 20, …`, not numeric order. That
is why the stores read `20, 34, 4, 41, 42, 46, 49, 53, 6, 9`. A numeric-order draw with the same seed
would pick a different set. The published result is for the set actually run, and
`tests/test_transfer.py` pins that set so the clarification cannot silently drift.

---

## 5. Plots

Generated by `make plots` into `reports/plots/` and committed.

- `pred_vs_actual_44_BREAD-BAKERY.png` — the flagship family's largest series. Present because the
  volume-spread selection below lands on groceries, stationery and homeware: every category except
  the one this system is for. The family is fixed by the domain and the series by volume, so the
  choice cannot be steered by the scores — and the model does *worse* on this family than pooled
  (§4.2), so the figure argues against the headline rather than for it.
- `pred_vs_actual_<series>.png` — three more series (high/medium/low volume, chosen by volume, not
  by score) across the test window.
- `error_by_horizon.png` — error growth with horizon, model vs baselines.
- `feature_importance.png` — gain-based. Associational, not causal, and unreliable under correlated
  features (`lag_7` and `roll_mean_7` overlap heavily): evidence about what the model used, not
  about what drives demand.

---

## 6. Reproducing this file

```shell
cd ml
make install
make data        # needs Kaggle credentials + accepted competition rules
make baselines   # then fill §3 and COMMIT before continuing
make train
make evaluate    # fills §4.2 — scores the held-out window
make backtest    # fills §4.3 — retrains per fold, ~10 min
make ablation    # fills §4.5 — retrains 6 variants, ~20 min, validation only
make coldstart   # fills §4.6 — no retraining, validation only
make transfer    # fills §4.7 — unseen + rescaled series, shipped model, validation only
make family      # regenerates the §4.2 bakery table from committed predictions
make plots       # fills §5
```

Every number traces to a git SHA plus `configs/favorita.yaml`, which `make train` snapshots into
`data/artifacts/metadata.json` alongside the model.

---

## 7. Note: which horizon step the product shows (not a re-score)

No number above changes. Every horizon in this file counts from the **last observed day**. The
backend worker feeds complete days only, through *yesterday*, and asks for predictions starting
*today*; so *today* is step 1 and **tomorrow ("mañana", what *Producción sugerida* shows) is step 2**,
where the edge over the baselines is smaller than the d+1 figure (§4.2/§4.4 report the error by
horizon). Feeding today's partial sales to reach a true d+1 would bias the freshest lag low, so the
product labels the gap instead: "calculado con ventas hasta ayer".
