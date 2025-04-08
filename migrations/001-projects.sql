DROP TABLE IF EXISTS languages , projects , strings;

CREATE TABLE languages
  ( lang_id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , bcp47     text NOT NULL UNIQUE
  , lang_name text NOT NULL UNIQUE
  );

CREATE TABLE projects
  ( project_id          integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , project_name        text NOT NULL UNIQUE
  , owner_id            integer NOT NULL REFERENCES users ( user_id )
  , source_lang_id      integer NOT NULL REFERENCES languages ( lang_id )
  , project_description text NOT NULL DEFAULT ''
  );

CREATE TABLE strings
  ( string_id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , project_id  integer NOT NULL
                        REFERENCES projects ( project_id ) ON DELETE CASCADE
  , string_name text NOT NULL
  , string_text text NOT NULL
  , UNIQUE ( project_id , string_name )
  );
