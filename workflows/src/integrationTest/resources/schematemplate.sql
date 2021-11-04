CREATE USER "$" WITH LOGIN PASSWORD 'pass1234';
CREATE SCHEMA "$_schema";
GRANT USAGE, CREATE ON SCHEMA "$_schema" TO "$";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "$_schema" TO "$";
ALTER DEFAULT privileges IN SCHEMA "$_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "$";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "$_schema" TO "$";
ALTER DEFAULT privileges IN SCHEMA "$_schema" GRANT USAGE, SELECT ON sequences TO "$";
ALTER ROLE "$" SET search_path = "$_schema";