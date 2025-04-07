DROP TABLE IF EXISTS users , roles , user_roles;

CREATE TABLE users
  ( user_id       integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , user_email    text NOT NULL UNIQUE
  , user_name     text NOT NULL
  , password_hash text NOT NULL
  );

CREATE TABLE roles
  ( role_id   integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY
  , role_name text NOT NULL UNIQUE
  );

CREATE TABLE user_roles
  ( user_id integer REFERENCES users ( user_id ) ON DELETE CASCADE
  , role_id integer REFERENCES roles ( role_id ) ON DELETE CASCADE
  , PRIMARY KEY ( user_id, role_id )
  );
