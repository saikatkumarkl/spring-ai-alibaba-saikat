--
-- Create the 'keycloak' database for Keycloak IDM.
-- PostgreSQL's docker-entrypoint-initdb.d scripts run against the default
-- database ('admin'). Keycloak needs its own database.
--

SELECT 'CREATE DATABASE keycloak OWNER admin'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
