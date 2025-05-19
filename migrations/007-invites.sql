DROP TABLE IF EXISTS invites , invite_usages;

CREATE TABLE invites
  ( invite_id       integer PRIMARY KEY GENERATED ALWAYS AS IDENTITY
  , issuer_id       integer NOT NULL REFERENCES users ( user_id )
  , invite_code     text NOT NULL UNIQUE
  , max_usage_count integer NOT NULL DEFAULT 0 CHECK ( max_usage_count >= 0 )
  , is_active       boolean NOT NULL DEFAULT true
  );

CREATE TABLE invite_usages
  ( user_id       integer PRIMARY KEY
                          REFERENCES users ( user_id )
                          ON DELETE CASCADE
  , invite_id     integer NOT NULL REFERENCES invites ( invite_id )
  , registered_at timestamptz NOT NULL DEFAULT NOW()
  );

CREATE OR REPLACE FUNCTION invite_usage_trigger () RETURNS TRIGGER
AS $$
DECLARE max_count integer;
BEGIN
  SELECT max_usage_count INTO max_count
    FROM invites
   WHERE invite_id = NEW.invite_id;

  IF max_count <> 0 AND max_count <=
     ( SELECT COUNT ( * )
         FROM invite_usages
        WHERE invite_id = NEW.invite_id )
  THEN
    RAISE EXCEPTION 'Max usage count of the invite is reached (%)'
                  , max_count;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER invite_usage_trigger
BEFORE INSERT ON invite_usages
   FOR EACH ROW EXECUTE FUNCTION invite_usage_trigger ();
