CREATE OR REPLACE PROCEDURE upload_strings ( project_id  integer
                                           , string_data json )
LANGUAGE SQL
AS $$
  MERGE INTO strings AS s
  USING ( SELECT *
            FROM json_each_text ( string_data )
        ) AS j
     ON ( s.project_id , s.string_name ) = ( project_id , j.key )
   WHEN MATCHED THEN UPDATE SET string_text = j.value
   WHEN NOT MATCHED BY SOURCE AND s.project_id = project_id THEN DELETE
   WHEN NOT MATCHED BY TARGET THEN
        INSERT ( project_id , string_name , string_text )
        VALUES ( project_id , j.key , j.value );
$$;
