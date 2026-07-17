-- Run this script as a MySQL administrator after replacing CHANGE_ME.
-- If the application connects from another host, adjust 'localhost' accordingly.

CREATE DATABASE IF NOT EXISTS research_pilot
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'research_pilot'@'localhost'
    IDENTIFIED BY 'CHANGE_ME';

ALTER USER 'research_pilot'@'localhost'
    IDENTIFIED BY 'CHANGE_ME';

GRANT ALL PRIVILEGES ON research_pilot.*
    TO 'research_pilot'@'localhost';

FLUSH PRIVILEGES;
