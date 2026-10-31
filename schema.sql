--
-- PostgreSQL database dump
--

\restrict tP6P4hw74L0NQezaVvtuTNJ553mw5fd51cSsMSMHpMPmIwYYEnaxqv88cS84xhZ

-- Dumped from database version 18.3 (Postgres.app)
-- Dumped by pg_dump version 18.3 (Postgres.app)

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

--
-- Name: schema; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA schema;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    company_id uuid NOT NULL,
    account_name character varying(255) NOT NULL,
    account_type character varying(50) NOT NULL,
    currency character varying(3) DEFAULT 'BYN'::character varying NOT NULL,
    balance numeric(15,2) DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_account_type_check CHECK (((account_type)::text = ANY ((ARRAY['CASH'::character varying, 'BANK'::character varying, 'CARD'::character varying, 'ELECTRONIC'::character varying])::text[])))
);


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid,
    action character varying(100) NOT NULL,
    entity_type character varying(100),
    entity_id character varying(100),
    old_value jsonb,
    new_value jsonb,
    ip_address inet,
    user_agent text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: budgets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.budgets (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    company_id uuid NOT NULL,
    year integer NOT NULL,
    month integer NOT NULL,
    planned_income numeric(15,2) DEFAULT 0,
    planned_expense numeric(15,2) DEFAULT 0,
    actual_income numeric(15,2) DEFAULT 0,
    actual_expense numeric(15,2) DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT budgets_month_check CHECK (((month >= 1) AND (month <= 12)))
);


--
-- Name: cash_flow_forecasts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_flow_forecasts (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    company_id uuid NOT NULL,
    forecast_period_start date NOT NULL,
    forecast_period_end date NOT NULL,
    total_projected_inflow numeric(15,2) DEFAULT 0,
    total_projected_outflow numeric(15,2) DEFAULT 0,
    net_cash_flow numeric(15,2) DEFAULT 0,
    strategy_used character varying(100),
    scenario character varying(30) DEFAULT 'BASE',
    parameters jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: companies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.companies (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    tax_id character varying(50),
    address text,
    phone character varying(20),
    owner_id uuid NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    description text,
    website_url character varying(255),
    logo_url text,
    status character varying(50) DEFAULT 'ACTIVE'::character varying
);


--
-- Name: forecast_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.forecast_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    forecast_id uuid NOT NULL,
    forecast_date date NOT NULL,
    projected_inflow numeric(15,2) DEFAULT 0,
    projected_outflow numeric(15,2) DEFAULT 0,
    actual_inflow numeric(15,2) DEFAULT 0,
    actual_outflow numeric(15,2) DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: integrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integrations (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    integration_type character varying(50) NOT NULL,
    access_token text,
    refresh_token text,
    token_expires_at timestamp without time zone,
    settings jsonb,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT integrations_integration_type_check CHECK (((integration_type)::text = ANY ((ARRAY['GOOGLE'::character varying, 'EXCHANGE_RATE'::character varying, 'BANK_API'::character varying])::text[])))
);


--
-- Name: payment_calendar; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_calendar (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    company_id uuid NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    amount numeric(15,2) NOT NULL,
    due_date date NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying,
    category character varying(100),
    counterparty character varying(255),
    created_by uuid,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: payment_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_requests (
    id uuid NOT NULL,
    account_id uuid,
    title character varying(255),
    amount numeric(19,2),
    date date,
    status character varying(50),
    requester character varying(255),
    counterparty character varying(255),
    category character varying(255),
    description text
);


--
-- Name: reference_cash_flow_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reference_cash_flow_items (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(20) NOT NULL,
    description text,
    CONSTRAINT reference_cash_flow_items_type_check CHECK (((type)::text = ANY ((ARRAY['INCOME'::character varying, 'EXPENSE'::character varying])::text[])))
);


--
-- Name: reference_cfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reference_cfo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    description text
);


--
-- Name: reference_counterparties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reference_counterparties (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(255) NOT NULL,
    tax_id character varying(50),
    address text,
    phone character varying(50),
    email character varying(255)
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id integer NOT NULL,
    name character varying(50) NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transactions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    account_id uuid NOT NULL,
    transaction_type character varying(20) NOT NULL,
    amount numeric(15,2) NOT NULL,
    date date NOT NULL,
    description text,
    category character varying(100),
    counterparty character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    cfo_id uuid,
    CONSTRAINT transactions_amount_check CHECK ((amount > (0)::numeric)),
    CONSTRAINT transactions_transaction_type_check CHECK (((transaction_type)::text = ANY ((ARRAY['INCOME'::character varying, 'EXPENSE'::character varying])::text[])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(255) NOT NULL,
    role_id integer NOT NULL,
    is_active boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: budgets budgets_company_id_year_month_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_company_id_year_month_key UNIQUE (company_id, year, month);


--
-- Name: budgets budgets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_pkey PRIMARY KEY (id);


--
-- Name: cash_flow_forecasts cash_flow_forecasts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_flow_forecasts
    ADD CONSTRAINT cash_flow_forecasts_pkey PRIMARY KEY (id);


--
-- Name: companies companies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_pkey PRIMARY KEY (id);


--
-- Name: companies companies_tax_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_tax_id_key UNIQUE (tax_id);


--
-- Name: forecast_items forecast_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecast_items
    ADD CONSTRAINT forecast_items_pkey PRIMARY KEY (id);


--
-- Name: integrations integrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integrations
    ADD CONSTRAINT integrations_pkey PRIMARY KEY (id);


--
-- Name: payment_calendar payment_calendar_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_calendar
    ADD CONSTRAINT payment_calendar_pkey PRIMARY KEY (id);


--
-- Name: payment_requests payment_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_requests
    ADD CONSTRAINT payment_requests_pkey PRIMARY KEY (id);


--
-- Name: reference_cash_flow_items reference_cash_flow_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reference_cash_flow_items
    ADD CONSTRAINT reference_cash_flow_items_pkey PRIMARY KEY (id);


--
-- Name: reference_cfo reference_cfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reference_cfo
    ADD CONSTRAINT reference_cfo_pkey PRIMARY KEY (id);


--
-- Name: reference_counterparties reference_counterparties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reference_counterparties
    ADD CONSTRAINT reference_counterparties_pkey PRIMARY KEY (id);


--
-- Name: roles roles_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_payment_calendar_company; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_calendar_company ON public.payment_calendar USING btree (company_id);


--
-- Name: idx_payment_calendar_due_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_calendar_due_date ON public.payment_calendar USING btree (due_date);


--
-- Name: idx_payment_calendar_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_calendar_status ON public.payment_calendar USING btree (status);

CREATE INDEX IF NOT EXISTS idx_accounts_company_id ON public.accounts(company_id);
CREATE INDEX IF NOT EXISTS idx_budgets_company_id ON public.budgets(company_id);
CREATE INDEX IF NOT EXISTS idx_cash_flow_forecasts_company_id ON public.cash_flow_forecasts(company_id);
CREATE INDEX IF NOT EXISTS idx_companies_owner_id ON public.companies(owner_id);
CREATE INDEX IF NOT EXISTS idx_forecast_items_forecast_id ON public.forecast_items(forecast_id);
CREATE INDEX IF NOT EXISTS idx_integrations_user_id ON public.integrations(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON public.transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON public.transactions(date);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON public.transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_payment_requests_account_id ON public.payment_requests(account_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON public.audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON public.audit_logs(user_id);



--
-- Name: accounts accounts_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: audit_logs audit_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: budgets budgets_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.budgets
    ADD CONSTRAINT budgets_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: cash_flow_forecasts cash_flow_forecasts_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_flow_forecasts
    ADD CONSTRAINT cash_flow_forecasts_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: companies companies_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.companies
    ADD CONSTRAINT companies_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);


--
-- Name: forecast_items forecast_items_forecast_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.forecast_items
    ADD CONSTRAINT forecast_items_forecast_id_fkey FOREIGN KEY (forecast_id) REFERENCES public.cash_flow_forecasts(id) ON DELETE CASCADE;


--
-- Name: integrations integrations_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integrations
    ADD CONSTRAINT integrations_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: payment_calendar payment_calendar_company_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_calendar
    ADD CONSTRAINT payment_calendar_company_id_fkey FOREIGN KEY (company_id) REFERENCES public.companies(id);


--
-- Name: payment_calendar payment_calendar_created_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_calendar
    ADD CONSTRAINT payment_calendar_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.payment_requests
    ADD CONSTRAINT payment_requests_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: transactions transactions_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: transactions transactions_cfo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_cfo_id_fkey FOREIGN KEY (cfo_id) REFERENCES public.reference_cfo(id);


--
-- Name: users users_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- PostgreSQL database dump complete
--

\unrestrict tP6P4hw74L0NQezaVvtuTNJ553mw5fd51cSsMSMHpMPmIwYYEnaxqv88cS84xhZ

