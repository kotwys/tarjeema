DROP TABLE IF EXISTS translations;

CREATE TABLE translations
  ( translation_id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , string_id        integer NOT NULL
                             REFERENCES strings ( string_id ) ON DELETE CASCADE
  , lang_id          integer NOT NULL REFERENCES languages ( lang_id )
  , translation_text text NOT NULL
  , user_id          integer NOT NULL REFERENCES users ( user_id )
  , suggested_at     timestamptz NOT NULL DEFAULT now()
  );

CREATE INDEX ON translations ( lang_id , string_id );
