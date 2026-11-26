ALTER TABLE doctors
    ADD COLUMN phone VARCHAR(25),
    ADD COLUMN qualification VARCHAR(160),
    ADD COLUMN years_of_experience INT,
    ADD CHECK (years_of_experience BETWEEN 0 AND 100);

ALTER TABLE patients
    MODIFY COLUMN date_of_birth DATE,
    MODIFY COLUMN gender VARCHAR(20),
    MODIFY COLUMN address VARCHAR(500);
