UPDATE concepts
SET concept_description = "모든 수 a에 대하여 $\\sqrt{a^2}=|a|=\\begin{cases}{a\\geq0,}\\sqrt[]{a^2}=a \\\\\\ {a<0}, \\sqrt[]{(-a)^2}=a\\end{cases}$\\n(모든 수 $a$에 대하여 $\\sqrt{a^2}$는 |a|와 같고,  $a$가 $0$보다 크거나 같을 때 $\\sqrt{a^2}$은 a이고, $a$가 $0$보다 작을 때 $\\sqrt{a^2}$은 $-a$이다.)"
WHERE concept_id = 4709;

SELECT * FROM concepts WHERE concept_id = 4709;
