const calculateGrade = (birthdate) => {
    if (!birthdate) {
        return ''; // birthdate가 비어있는 경우 빈 문자열 반환
    }
    const today = new Date();
    const birth = new Date(birthdate);
    const age = today.getFullYear() - birth.getFullYear()+1;

    if (age >= 8 && age <= 13) {
        return `초${age - 7}`;
    } else if (age >= 14 && age <= 16) {
        return `중${age - 13}`;
    } else if (age >= 17 && age <= 19) {
        return `고${age - 16}`;
    } else {
        return '';
    }
};

const padZero = (num) => (num < 10 ? `0${num}` : num.toString());
const getDayOfWeek = (dayIndex) => {
  const daysOfWeek = ['일', '월', '화', '수', '목', '금', '토'];
  return daysOfWeek[dayIndex];
};
const updateDate = () => {
  const now = new Date();
  const year = now.getFullYear().toString().slice(2); // 뒤의 두 자리만 사용
  const month = padZero(now.getMonth() + 1); // getMonth()는 0부터 시작하므로 1을 더하고 0을 채움
  const day = padZero(now.getDate());
  const dayOfWeek = getDayOfWeek(now.getDay());
  return `${year}/${month}/${day}(${dayOfWeek})`;
};

const title = {
    calculateGrade,
    updateDate,
};

export default title;
