create table job_output (
    gp_job_no integer not null references ANALYSIS_JOB(JOB_NO) on delete cascade,
    path varchar(255) not null,
    gpFileType varchar(255),
    extension varchar(255),
    kind varchar(255),
    file_length bigint unsigned,
    last_modified timestamp null,
    hidden bit not null,
    deleted bit not null,
    primary key (gp_job_no,path),
    unique (gp_job_no, path));

create table queue_congestion (
    id bigint not null auto_increment,
    queuetime bigint unsigned not null,
    queue varchar(255),
    primary key (id),
    unique (queue));

-- update schema version
update props set value='3.9.0' where `key`='schemaVersion';

commit;

