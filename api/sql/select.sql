select count(*) from concepts;
select count(*) from knowledge_space;
select count(*) from items;

select count(*) from tests;
select * from items where item_id>4890;

select count(*) from tests_items;

select * from concepts where concept_school_level = '고등';

select * from users;

select count(distinct concept_chapter_main) from concepts;
select count(distinct concept_chapter_subsub) from concepts;
