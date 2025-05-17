DROP TABLE IF EXISTS proofreaders;

CREATE TABLE proofreaders
  ( project_id integer REFERENCES projects ( project_id ) ON DELETE CASCADE
  , user_id    integer REFERENCES users ( user_id ) ON DELETE CASCADE
  , lang_id    integer REFERENCES languages ( lang_id ) ON DELETE CASCADE
  , PRIMARY KEY ( project_id , user_id , lang_id )
  );
