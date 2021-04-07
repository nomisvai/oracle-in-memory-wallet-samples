--liquibase formatted sql

--changeset sample:user
CREATE TABLE users
(
    id   VARCHAR2(255) NOT NULL,
    name VARCHAR2(255) NOT NULL,
    CONSTRAINT user_id_pk PRIMARY KEY (id)
);
