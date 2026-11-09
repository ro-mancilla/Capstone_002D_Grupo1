-- =====================================================================================
-- EcoPan: DDL del modelo de datos (PostgreSQL 17)
--
-- Esquema completo de la base de datos del backend, obtenido con pg_dump --schema-only
-- sobre una base creada desde cero por el código del sistema.
--
-- Las 18 tablas de negocio corresponden al diagrama modelo_datos_er.png. Además se
-- incluyen tres tablas técnicas que no aparecen en el diagrama:
--   outbox_messages  mensajes pendientes de publicar al broker (patrón outbox)
--   scheduled_runs   control de las tareas programadas (actualización del pronóstico)
--   seed_revisions   versión de los datos de referencia cargados
-- =====================================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: branches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.branches (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name text NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    server_seq bigint NOT NULL
);


--
-- Name: forecasts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forecasts (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    product_id uuid NOT NULL,
    product_type character varying(48) NOT NULL,
    product_type_id uuid NOT NULL,
    forecast_date date NOT NULL,
    predicted_grams bigint,
    predicted_units integer,
    model_version character varying(64) NOT NULL,
    generated_at timestamp without time zone NOT NULL,
    server_seq bigint NOT NULL
);


--
-- Name: formula_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.formula_lines (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    formula_id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    basis_points integer NOT NULL
);


--
-- Name: formulas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.formulas (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    product_id uuid NOT NULL,
    active boolean DEFAULT true NOT NULL,
    server_seq bigint NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: ingredients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ingredients (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name text NOT NULL,
    low_stock_threshold_grams bigint,
    active boolean DEFAULT true NOT NULL,
    server_seq bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: outbox_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_messages (
    id uuid NOT NULL,
    topic character varying(64) NOT NULL,
    payload text NOT NULL,
    created_at timestamp without time zone NOT NULL,
    available_at timestamp without time zone NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    last_error text,
    sent_at timestamp without time zone
);


--
-- Name: product_stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_stock_movements (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    product_id uuid NOT NULL,
    delta_units bigint NOT NULL,
    kind character varying(20) NOT NULL,
    note text,
    created_by uuid,
    occurred_at timestamp without time zone NOT NULL,
    received_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_product_stock_movements_kind CHECK (((kind)::text = ANY ((ARRAY['OPENING_COUNT'::character varying, 'PURCHASE'::character varying, 'MANUAL_ADJUSTMENT'::character varying])::text[])))
);


--
-- Name: product_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_types (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name character varying(48) NOT NULL,
    key character varying(96) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: production_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.production_batches (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    baker_id uuid NOT NULL,
    product_id uuid NOT NULL,
    mix_weight_grams bigint NOT NULL,
    batch_at timestamp without time zone NOT NULL,
    movements_minted boolean NOT NULL,
    decrement_status character varying(12) NOT NULL,
    device_id character varying(64) NOT NULL,
    server_seq bigint NOT NULL,
    received_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_production_batches_decrement_status CHECK (((decrement_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying, 'SKIPPED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    name text NOT NULL,
    product_type character varying(48) NOT NULL,
    product_type_id uuid NOT NULL,
    sold_by character varying(8) NOT NULL,
    origin character varying(16) NOT NULL,
    price_per_kg_minor bigint,
    price_minor bigint,
    barcode character varying(64),
    image_url text,
    low_stock_threshold_units bigint,
    active boolean DEFAULT true NOT NULL,
    server_seq bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_products_origin CHECK (((origin)::text = ANY ((ARRAY['PANADERIA'::character varying, 'ALMACEN'::character varying])::text[]))),
    CONSTRAINT ck_products_sold_by CHECK (((sold_by)::text = ANY ((ARRAY['WEIGHT'::character varying, 'UNIT'::character varying])::text[])))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(128) NOT NULL,
    device_id character varying(64),
    device_name text,
    branch_id uuid,
    issued_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    revoked_at timestamp without time zone,
    family_id uuid
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role character varying(24) NOT NULL,
    permission character varying(48) NOT NULL,
    CONSTRAINT ck_role_permissions_permission CHECK (((permission)::text = ANY ((ARRAY['SALES_RECORD'::character varying, 'BATCH_RECORD'::character varying, 'STOCK_ADJUST'::character varying, 'STOCK_READ'::character varying, 'CATALOG_READ'::character varying, 'CATALOG_WRITE'::character varying, 'ANALYTICS_READ'::character varying, 'FORECAST_READ'::character varying, 'USERS_MANAGE'::character varying, 'MODULES_TOGGLE'::character varying, 'SYNC'::character varying])::text[])))
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    name character varying(24) NOT NULL
);


--
-- Name: sale_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_lines (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    sale_id uuid NOT NULL,
    product_id uuid NOT NULL,
    sold_by character varying(8) NOT NULL,
    weight_grams bigint,
    quantity integer,
    unit_price_minor bigint NOT NULL,
    line_total_minor bigint NOT NULL,
    CONSTRAINT ck_sale_lines_sold_by CHECK (((sold_by)::text = ANY ((ARRAY['WEIGHT'::character varying, 'UNIT'::character varying])::text[])))
);


--
-- Name: sales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    cashier_id uuid NOT NULL,
    sold_at timestamp without time zone NOT NULL,
    payment_method character varying(16) NOT NULL,
    total_minor bigint NOT NULL,
    device_id character varying(64) NOT NULL,
    server_seq bigint NOT NULL,
    received_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_sales_payment_method CHECK (((payment_method)::text = ANY ((ARRAY['CASH'::character varying, 'CARD'::character varying, 'TRANSFER'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: scheduled_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scheduled_runs (
    job character varying(64) NOT NULL,
    run_date date NOT NULL,
    scope character varying(64) NOT NULL,
    claimed_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone
);


--
-- Name: seed_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.seed_revisions (
    profile character varying(64) NOT NULL,
    revision integer NOT NULL,
    applied_at timestamp without time zone NOT NULL
);


--
-- Name: server_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.server_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock_movements (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    delta_grams bigint NOT NULL,
    kind character varying(20) NOT NULL,
    source_batch_id uuid,
    note text,
    created_by uuid,
    occurred_at timestamp without time zone NOT NULL,
    server_seq bigint NOT NULL,
    received_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_stock_movements_kind CHECK (((kind)::text = ANY ((ARRAY['PURCHASE'::character varying, 'FORMULA_DECREMENT'::character varying, 'MANUAL_ADJUSTMENT'::character varying])::text[])))
);


--
-- Name: tenant_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_modules (
    tenant_id uuid NOT NULL,
    module character varying(32) NOT NULL,
    CONSTRAINT ck_tenant_modules_module CHECK (((module)::text = ANY ((ARRAY['POS_SALES'::character varying, 'INVENTORY_FORMULAS'::character varying, 'DEMAND_FORECASTING'::character varying, 'MULTI_BRANCH_ROLES'::character varying, 'ANALYTICS_DASHBOARD'::character varying])::text[])))
);


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenants (
    id uuid NOT NULL,
    slug character varying(64) NOT NULL,
    name text NOT NULL,
    currency_code character varying(3) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    branch_id uuid,
    username character varying(64) NOT NULL,
    password_hash text NOT NULL,
    pin_hash text,
    display_name text NOT NULL,
    role character varying(24) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    server_seq bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: branches branches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT branches_pkey PRIMARY KEY (id);


--
-- Name: forecasts forecasts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT forecasts_pkey PRIMARY KEY (id);


--
-- Name: formula_lines formula_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formula_lines
    ADD CONSTRAINT formula_lines_pkey PRIMARY KEY (id);


--
-- Name: formulas formulas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formulas
    ADD CONSTRAINT formulas_pkey PRIMARY KEY (id);


--
-- Name: ingredients ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT ingredients_pkey PRIMARY KEY (id);


--
-- Name: outbox_messages outbox_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_messages
    ADD CONSTRAINT outbox_messages_pkey PRIMARY KEY (id);


--
-- Name: role_permissions pk_role_permissions; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT pk_role_permissions PRIMARY KEY (role, permission);


--
-- Name: scheduled_runs pk_scheduled_runs; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scheduled_runs
    ADD CONSTRAINT pk_scheduled_runs PRIMARY KEY (job, run_date, scope);


--
-- Name: tenant_modules pk_tenant_modules; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_modules
    ADD CONSTRAINT pk_tenant_modules PRIMARY KEY (tenant_id, module);


--
-- Name: product_stock_movements product_stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_stock_movements
    ADD CONSTRAINT product_stock_movements_pkey PRIMARY KEY (id);


--
-- Name: product_types product_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_types
    ADD CONSTRAINT product_types_pkey PRIMARY KEY (id);


--
-- Name: production_batches production_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT production_batches_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_hash_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_unique UNIQUE (token_hash);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (name);


--
-- Name: sale_lines sale_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_lines
    ADD CONSTRAINT sale_lines_pkey PRIMARY KEY (id);


--
-- Name: sales sales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_pkey PRIMARY KEY (id);


--
-- Name: seed_revisions seed_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.seed_revisions
    ADD CONSTRAINT seed_revisions_pkey PRIMARY KEY (profile);


--
-- Name: stock_movements stock_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT stock_movements_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_slug_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_slug_unique UNIQUE (slug);


--
-- Name: forecasts uq_forecasts_tenant_branch_product_date; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT uq_forecasts_tenant_branch_product_date UNIQUE (tenant_id, branch_id, product_id, forecast_date);


--
-- Name: formula_lines uq_formula_lines_formula_ingredient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formula_lines
    ADD CONSTRAINT uq_formula_lines_formula_ingredient UNIQUE (formula_id, ingredient_id);


--
-- Name: formulas uq_formulas_tenant_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formulas
    ADD CONSTRAINT uq_formulas_tenant_product UNIQUE (tenant_id, product_id);


--
-- Name: product_types uq_product_types_tenant_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_types
    ADD CONSTRAINT uq_product_types_tenant_id UNIQUE (tenant_id, id);


--
-- Name: product_types uq_product_types_tenant_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_types
    ADD CONSTRAINT uq_product_types_tenant_key UNIQUE (tenant_id, key);


--
-- Name: stock_movements uq_stock_movements_source_batch_ingredient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT uq_stock_movements_source_batch_ingredient UNIQUE (source_batch_id, ingredient_id);


--
-- Name: users uq_users_tenant_username; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_tenant_username UNIQUE (tenant_id, username);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_branches_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_branches_tenant_seq ON public.branches USING btree (tenant_id, server_seq);


--
-- Name: idx_forecasts_tenant_branch_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forecasts_tenant_branch_date ON public.forecasts USING btree (tenant_id, branch_id, forecast_date);


--
-- Name: idx_forecasts_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_forecasts_tenant_seq ON public.forecasts USING btree (tenant_id, server_seq);


--
-- Name: idx_ingredients_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ingredients_tenant_seq ON public.ingredients USING btree (tenant_id, server_seq);


--
-- Name: idx_outbox_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outbox_pending ON public.outbox_messages USING btree (sent_at, available_at);


--
-- Name: idx_product_stock_movements_tenant_branch_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_stock_movements_tenant_branch_product ON public.product_stock_movements USING btree (tenant_id, branch_id, product_id);


--
-- Name: idx_production_batches_tenant_branch_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_production_batches_tenant_branch_at ON public.production_batches USING btree (tenant_id, branch_id, batch_at);


--
-- Name: idx_production_batches_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_production_batches_tenant_seq ON public.production_batches USING btree (tenant_id, server_seq);


--
-- Name: idx_products_tenant_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_tenant_active ON public.products USING btree (tenant_id, active);


--
-- Name: idx_products_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_products_tenant_seq ON public.products USING btree (tenant_id, server_seq);


--
-- Name: idx_refresh_tokens_family; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_family ON public.refresh_tokens USING btree (family_id);


--
-- Name: idx_refresh_tokens_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_sale_lines_tenant_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sale_lines_tenant_product ON public.sale_lines USING btree (tenant_id, product_id);


--
-- Name: idx_sale_lines_tenant_sale; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sale_lines_tenant_sale ON public.sale_lines USING btree (tenant_id, sale_id);


--
-- Name: idx_sales_tenant_branch_receivedat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_tenant_branch_receivedat ON public.sales USING btree (tenant_id, branch_id, received_at);


--
-- Name: idx_sales_tenant_branch_soldat; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_tenant_branch_soldat ON public.sales USING btree (tenant_id, branch_id, sold_at);


--
-- Name: idx_sales_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_tenant_seq ON public.sales USING btree (tenant_id, server_seq);


--
-- Name: idx_stock_movements_tenant_branch_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_movements_tenant_branch_occurred ON public.stock_movements USING btree (tenant_id, branch_id, occurred_at);


--
-- Name: idx_stock_movements_tenant_ingredient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_movements_tenant_ingredient ON public.stock_movements USING btree (tenant_id, ingredient_id);


--
-- Name: idx_stock_movements_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stock_movements_tenant_seq ON public.stock_movements USING btree (tenant_id, server_seq);


--
-- Name: idx_users_tenant_seq; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_tenant_seq ON public.users USING btree (tenant_id, server_seq);


--
-- Name: uq_products_tenant_barcode; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_products_tenant_barcode ON public.products USING btree (tenant_id, barcode) WHERE (barcode IS NOT NULL);


--
-- Name: branches fk_branches_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.branches
    ADD CONSTRAINT fk_branches_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: forecasts fk_forecasts_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT fk_forecasts_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: forecasts fk_forecasts_product_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT fk_forecasts_product_id__id FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: forecasts fk_forecasts_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT fk_forecasts_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: forecasts fk_forecasts_tenant_product_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecasts
    ADD CONSTRAINT fk_forecasts_tenant_product_type FOREIGN KEY (tenant_id, product_type_id) REFERENCES public.product_types(tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: formula_lines fk_formula_lines_formula_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formula_lines
    ADD CONSTRAINT fk_formula_lines_formula_id__id FOREIGN KEY (formula_id) REFERENCES public.formulas(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: formula_lines fk_formula_lines_ingredient_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formula_lines
    ADD CONSTRAINT fk_formula_lines_ingredient_id__id FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: formula_lines fk_formula_lines_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formula_lines
    ADD CONSTRAINT fk_formula_lines_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: formulas fk_formulas_product_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formulas
    ADD CONSTRAINT fk_formulas_product_id__id FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: formulas fk_formulas_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.formulas
    ADD CONSTRAINT fk_formulas_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: ingredients fk_ingredients_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT fk_ingredients_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: product_stock_movements fk_product_stock_movements_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_stock_movements
    ADD CONSTRAINT fk_product_stock_movements_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: product_stock_movements fk_product_stock_movements_product_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_stock_movements
    ADD CONSTRAINT fk_product_stock_movements_product_id__id FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: product_stock_movements fk_product_stock_movements_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_stock_movements
    ADD CONSTRAINT fk_product_stock_movements_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: product_types fk_product_types_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_types
    ADD CONSTRAINT fk_product_types_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE CASCADE;


--
-- Name: production_batches fk_production_batches_baker_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT fk_production_batches_baker_id__id FOREIGN KEY (baker_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: production_batches fk_production_batches_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT fk_production_batches_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: production_batches fk_production_batches_product_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT fk_production_batches_product_id__id FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: production_batches fk_production_batches_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.production_batches
    ADD CONSTRAINT fk_production_batches_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: products fk_products_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fk_products_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: products fk_products_tenant_product_type; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fk_products_tenant_product_type FOREIGN KEY (tenant_id, product_type_id) REFERENCES public.product_types(tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: refresh_tokens fk_refresh_tokens_user_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user_id__id FOREIGN KEY (user_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: role_permissions fk_role_permissions_role__name; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fk_role_permissions_role__name FOREIGN KEY (role) REFERENCES public.roles(name) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sale_lines fk_sale_lines_product_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_lines
    ADD CONSTRAINT fk_sale_lines_product_id__id FOREIGN KEY (product_id) REFERENCES public.products(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sale_lines fk_sale_lines_sale_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_lines
    ADD CONSTRAINT fk_sale_lines_sale_id__id FOREIGN KEY (sale_id) REFERENCES public.sales(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sale_lines fk_sale_lines_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_lines
    ADD CONSTRAINT fk_sale_lines_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sales fk_sales_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fk_sales_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sales fk_sales_cashier_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fk_sales_cashier_id__id FOREIGN KEY (cashier_id) REFERENCES public.users(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: sales fk_sales_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT fk_sales_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: stock_movements fk_stock_movements_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT fk_stock_movements_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: stock_movements fk_stock_movements_ingredient_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT fk_stock_movements_ingredient_id__id FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: stock_movements fk_stock_movements_source_batch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT fk_stock_movements_source_batch_id__id FOREIGN KEY (source_batch_id) REFERENCES public.production_batches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: stock_movements fk_stock_movements_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock_movements
    ADD CONSTRAINT fk_stock_movements_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: tenant_modules fk_tenant_modules_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_modules
    ADD CONSTRAINT fk_tenant_modules_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE CASCADE;


--
-- Name: users fk_users_branch_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_branch_id__id FOREIGN KEY (branch_id) REFERENCES public.branches(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: users fk_users_role__name; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_role__name FOREIGN KEY (role) REFERENCES public.roles(name) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- Name: users fk_users_tenant_id__id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_tenant_id__id FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON UPDATE RESTRICT ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--


