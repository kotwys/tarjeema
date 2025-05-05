DROP TABLE IF EXISTS translation_approvals;

CREATE TABLE translation_approvals
  ( translation_id integer PRIMARY KEY
                           REFERENCES translations ( translation_id )
                           ON DELETE CASCADE
  , user_id        integer NOT NULL REFERENCES users ( user_id )
  , approved_at    timestamptz NOT NULL DEFAULT now()
  );

-- Ensures that there has not been already a translation for this source string
-- approved.
CREATE OR REPLACE FUNCTION translation_approval_trigger () RETURNS trigger
AS $$
DECLARE approved_translation_id integer;
BEGIN
  SELECT translation_id INTO approved_translation_id
    FROM translations
    JOIN translation_approvals USING ( translation_id )
   WHERE translation_id != NEW.translation_id
     AND ( OLD IS NULL OR translation_id != OLD.translation_id )
     AND ( string_id , lang_id ) =
         ( SELECT string_id , lang_id
             FROM translations
            WHERE translation_id = NEW.translation_id );

  IF FOUND THEN
    RAISE EXCEPTION 'Translation % is already approved'
                  , approved_translation_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER translation_approval_trigger
BEFORE INSERT OR UPDATE OF translation_id
    ON translation_approvals
   FOR EACH ROW EXECUTE FUNCTION translation_approval_trigger ();
