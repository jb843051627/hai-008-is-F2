-- H2（MODE=MySQL）测试库：辨识定级相关四张表，每次测试前重建。
DROP TABLE IF EXISTS t_is_haz_card_review;
DROP TABLE IF EXISTS t_is_haz_card;
DROP TABLE IF EXISTS t_is_lim_rule;
DROP TABLE IF EXISTS t_is_enterprise;

CREATE TABLE t_is_enterprise (
  id bigint NOT NULL,
  site_no varchar(64),
  site_name varchar(128),
  site_type varchar(32),
  road_name varchar(128),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_is_lim_rule (
  id bigint NOT NULL,
  rule_code varchar(64),
  rule_name varchar(128),
  rule_source int,
  apply_type varchar(16),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  eff_start datetime,
  eff_end datetime,
  priority int,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_is_haz_card (
  id bigint NOT NULL,
  bill_no varchar(64),
  node_no int,
  site_id int,
  site_no varchar(64),
  qty decimal(12,2),
  fine_amt decimal(12,2),
  ident_date date,
  grade_level int,
  rule_code varchar(64),
  rule_eff_start datetime,
  no_standard int DEFAULT 0,
  content varchar(255),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_is_haz_card_review (
  id bigint NOT NULL,
  review_no varchar(64),
  card_id bigint NOT NULL,
  bill_no varchar(64),
  review_date date,
  qty decimal(12,2),
  ident_date date,
  orig_grade_level int,
  orig_rule_code varchar(64),
  orig_rule_eff_start datetime,
  review_grade_level int,
  review_rule_code varchar(64),
  review_rule_eff_start datetime,
  no_standard int DEFAULT 0,
  consistent int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);
