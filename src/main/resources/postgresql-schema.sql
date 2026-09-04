-- PostgreSQL 16 Schema Initialization Script for ModResorts Application
-- Migrated from SQL Server to PostgreSQL 16
--
-- Run this script to initialize the PostgreSQL database schema.
-- Usage: psql -U postgres -d modresorts -f postgresql-schema.sql

-- Create database (run as superuser if needed)
-- CREATE DATABASE modresorts;

-- Connect to the modresorts database
-- \c modresorts

-- ============================================================
-- Customer Table
-- Migrated from SQL Server: TABLE [dbo].[CUSTOMER] with column [INFO]
-- PostgreSQL uses lowercase unquoted identifiers by default
-- ============================================================
CREATE TABLE IF NOT EXISTS customer (
    id   SERIAL PRIMARY KEY,
    info TEXT NOT NULL
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_customer_id ON customer (id);

-- ============================================================
-- Sample Data (optional - for testing)
-- ============================================================
-- INSERT INTO customer (info) VALUES ('{"name": "John Doe", "email": "john.doe@example.com"}');
-- INSERT INTO customer (info) VALUES ('{"name": "Jane Smith", "email": "jane.smith@example.com"}');

-- ============================================================
-- Verify schema
-- ============================================================
-- SELECT table_name, column_name, data_type
-- FROM information_schema.columns
-- WHERE table_schema = 'public'
-- ORDER BY table_name, ordinal_position;
