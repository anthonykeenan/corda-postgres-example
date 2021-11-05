CREATE USER "notary" WITH LOGIN PASSWORD 'pass1234';
CREATE SCHEMA "notary_schema";
GRANT USAGE, CREATE ON SCHEMA "notary_schema" TO "notary";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "notary_schema" TO "notary";
ALTER DEFAULT privileges IN SCHEMA "notary_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "notary";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "notary_schema" TO "notary";
ALTER DEFAULT privileges IN SCHEMA "notary_schema" GRANT USAGE, SELECT ON sequences TO "notary";
ALTER ROLE "notary" SET search_path = "notary_schema";

CREATE USER "insurer" WITH LOGIN PASSWORD 'pass1234';
CREATE SCHEMA "insurer_schema";
GRANT USAGE, CREATE ON SCHEMA "insurer_schema" TO "insurer";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "insurer_schema" TO "insurer";
ALTER DEFAULT privileges IN SCHEMA "insurer_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "insurer";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "insurer_schema" TO "insurer";
ALTER DEFAULT privileges IN SCHEMA "insurer_schema" GRANT USAGE, SELECT ON sequences TO "insurer";
ALTER ROLE "insurer" SET search_path = "insurer_schema";

CREATE USER "insuree" WITH LOGIN PASSWORD 'pass1234';
CREATE SCHEMA "insuree_schema";
GRANT USAGE, CREATE ON SCHEMA "insuree_schema" TO "insuree";
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ALL tables IN SCHEMA "insuree_schema" TO "insuree";
ALTER DEFAULT privileges IN SCHEMA "insuree_schema" GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON tables TO "insuree";
GRANT USAGE, SELECT ON ALL sequences IN SCHEMA "insuree_schema" TO "insuree";
ALTER DEFAULT privileges IN SCHEMA "insuree_schema" GRANT USAGE, SELECT ON sequences TO "insuree";
ALTER ROLE "insuree" SET search_path = "insuree_schema";
