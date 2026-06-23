import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';

cytoscape.use(klay);

// ─── 크기 기본값 ───
const nodeSize = 7;
const fontSize = 7;
const edgeWidth = '2px';
const arrowScale = 0.8;
// ─── 색상 기본값 ───
const dimColor = '#dfe4ea';
const edgeColor = '#ced6e0';
const nodeColor = '#57606f'; // 글씨색
// ─── activate 시 크기 ───
const nodeActiveSize = 15;
const fontActiveSize = 11;
const edgeActiveWidth = '4px';
const arrowActiveScale = 1.2;
// ─── activate 시 색상 ───
const nodeActiveColor = '#6466f1'; // 선택한 노드
const fromColor = '#ff6348'; // 후수지식
const toColor = '#1e90ff'; // 선수지식

// 학년(conceptGradeLevel) → 노드 색상
const getNodeColor = (gradeLevel) => {
    switch (gradeLevel) {
        case '초1':
        case '초2':
            return 'yellow';
        case '초3':
        case '초4':
            return 'springGreen';
        case '초5':
        case '초6':
            return 'green';
        case '중1':
            return 'lightblue';
        case '중2':
            return 'dodgerblue';
        case '중3':
            return 'rgb(9, 106, 204)';
        case '수학':
            return 'lightpink';
        case '수1':
        case '수2':
            return 'hotpink';
        case '미적':
        case '확통':
        case '기하':
            return 'red';
        default:
            return 'gray';
    }
};

// 노드 속성에 따라 색상 변경 (초기 색상을 nodeMyColor 데이터에 저장)
const changeNodeColor = (cy) => {
    cy.nodes().forEach((node) => {
        const nodeData = node.data();
        const nodeMyColor = getNodeColor(nodeData.conceptGradeLevel);
        node.data('nodeMyColor', nodeMyColor);
        node.style('background-color', nodeMyColor);
    });
};

const setDimStyle = (target_cy, style) => {
    target_cy.nodes().forEach((target) => {
        target.style(style);
    });
    target_cy.edges().forEach((target) => {
        target.style(style);
    });
};

const setFocus = (target_element, fromColor, toColor, edgeWidth, arrowScale) => {
    if (!target_element) {
        console.error('Invalid target element.');
        return;
    }
    target_element.style('background-color', nodeActiveColor);
    target_element.style('width', Math.max(parseFloat(target_element.style('width')), nodeActiveSize));
    target_element.style('height', Math.max(parseFloat(target_element.style('height')), nodeActiveSize));
    target_element.style('font-size', Math.max(parseFloat(target_element.style('font-size')), fontActiveSize));
    target_element.style('color', nodeColor);
    target_element.successors().each((e) => {
        if (e.isEdge()) {
            e.style('width', edgeWidth);
            e.style('arrow-scale', arrowScale);
        }
        e.style('color', nodeColor);
        e.style('background-color', fromColor);
        e.style('line-color', fromColor);
        e.style('target-arrow-color', fromColor);
        e.style('opacity', 0.5);
    });
    target_element.predecessors().each((e) => {
        if (e.isEdge()) {
            e.style('width', edgeWidth);
            e.style('arrow-scale', arrowScale);
        }
        e.style('color', nodeColor);
        e.style('background-color', toColor);
        e.style('line-color', toColor);
        e.style('target-arrow-color', toColor);
        e.style('opacity', 0.5);
    });
    target_element.neighborhood().each((e) => {
        // 이웃한 엣지와 노드
        e.style('font-size', Math.max(parseFloat(e.style('font-size')), fontActiveSize));
        e.style('color', nodeColor);
        e.style('opacity', 1);
    });
};

const setResetFocus = (target_cy) => {
    target_cy.nodes().forEach((target) => {
        const originalColor = target.data('nodeMyColor');
        target.style('background-color', originalColor);
        target.style('width', nodeSize);
        target.style('height', nodeSize);
        target.style('font-size', fontSize);
        target.style('color', nodeColor);
        target.style('opacity', 1);
    });
    target_cy.edges().forEach(function (target) {
        target.style('line-color', edgeColor);
        target.style('target-arrow-color', edgeColor);
        target.style('width', edgeWidth);
        target.style('arrow-scale', arrowScale);
        target.style('opacity', 1);
    });
};

const buildCyStyle = () => [
    {
        selector: 'node',
        style: {
            'background-color': nodeColor,
            width: nodeSize,
            height: nodeSize,
            'font-size': fontSize,
            color: nodeColor,
            label: 'data(label)',
            'text-margin-y': -2,
            'text-wrap': 'wrap', // 텍스트 줄바꿈 설정
            'text-max-width': '60px' // 텍스트 최대 가로 길이 설정
        }
    },
    {
        selector: 'edge',
        style: {
            width: edgeWidth,
            'curve-style': 'bezier',
            'line-color': edgeColor, //#ccc
            'target-arrow-color': edgeColor, //#ccc
            'target-arrow-shape': 'triangle',
            'arrow-scale': arrowScale
        }
    }
];

const buildCyLayout = () => ({
    name: 'klay',
    animate: false,
    gravityRangeCompound: 1.5,
    klay: {
        spacing: 26
    },
    fit: true, // 레이아웃을 컨테이너에 맞게 자동 조정
    tile: true // 타일형 레이아웃 (노드를 격자로 배치)
});

/**
 * 개념 지식 그래프(Cytoscape+klay) 렌더링 레이어.
 * 데이터 적재(knowledgeSpace 조립·누적/리셋)는 각 뷰가 소유하고,
 * 이 컴포저블은 cy 인스턴스 생성·스타일·인터랙션 배선·파기만 담당한다.
 */
export function useConceptGraph() {
    let cy = null;

    /**
     * @param {HTMLElement} containerEl - 그래프 컨테이너 DOM
     * @param {Array} elements - cytoscape elements (nodes + edges)
     * @param {{ onTapNode?: (id: string) => void }} [opts]
     */
    const initGraph = (containerEl, elements, opts = {}) => {
        if (!containerEl) return null;
        cy = cytoscape({
            container: containerEl,
            elements,
            style: buildCyStyle(),
            layout: buildCyLayout()
        });
        // 노드 속성에 따라 색상 변경
        changeNodeColor(cy);

        // 클릭한 id 추출 (상세보기에 뿌려주기 위해)
        cy.on('tap', 'node', (event) => {
            if (opts.onTapNode) {
                opts.onTapNode(event.target.id());
            }
        });

        // 마우스 인/아웃 하이라이트
        cy.on('tapstart mouseover', 'node', (e) => {
            setDimStyle(cy, {
                'background-color': dimColor,
                'line-color': dimColor,
                'source-arrow-color': dimColor,
                color: dimColor
            });
            setFocus(e.target, fromColor, toColor, edgeActiveWidth, arrowActiveScale);
        });
        cy.on('tapend mouseout', 'node', (e) => {
            setResetFocus(e.cy);
        });
        return cy;
    };

    // Cytoscape 인스턴스 파기 (컴포넌트 onBeforeUnmount / 재조회 시 호출)
    const destroy = () => {
        if (cy) {
            cy.destroy();
            cy = null;
        }
    };

    return { initGraph, destroy, getNodeColor };
}
