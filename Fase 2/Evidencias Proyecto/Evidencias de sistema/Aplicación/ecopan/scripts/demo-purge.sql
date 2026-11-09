-- Clears transactional data, keeps the catalog + photos. Shared by scripts/demo-up.sh and
-- infra/import-local.sh so the local and Azure resets cannot drift apart.
DO $$
DECLARE t text;
BEGIN
  -- Only tables that exist (a database that has never been booted has none), and only
  -- transactional ones. CASCADE follows foreign keys *into* these tables (sale_lines,
  -- stock_movements), never out of them -- products, users, formulas are untouched.
  FOREACH t IN ARRAY ARRAY['sales','sale_lines','production_batches','stock_movements',
                           'forecasts','refresh_tokens']
  LOOP
    IF to_regclass('public.' || t) IS NOT NULL THEN
      EXECUTE format('TRUNCATE TABLE %I CASCADE', t);
    END IF;
  END LOOP;
  -- Almacén stock = OPENING_COUNT + later movements - units sold since the count. Sales are gone,
  -- so dropping the later movements puts every product back at its opening count, and keeping the
  -- count itself is what keeps a product tracked at all (no count = no stock figure on the till).
  IF to_regclass('public.product_stock_movements') IS NOT NULL THEN
    DELETE FROM product_stock_movements WHERE kind <> 'OPENING_COUNT';
  END IF;
END $$;
