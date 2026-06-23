import cytoscape from 'cytoscape';
import klay from 'cytoscape-klay';

cytoscape.use(klay);

// ─── 크기 기본값 (7px → 가독 가능한 크기로 상향, spec-03 Task3) ───
const nodeSize = 14;
const fontSize = 11;
const edgeWidth = '2px';
const arrowScale = 0.8;
// ─── 색상 기본값 ───
const dimColor = '#dfe4ea';
const edgeColor = '#ced6e0';
const nodeColor = '#57606f'; // 글씨색
// ─── activate 시 크기 (base 보다 커야 Math.max 로 강조됨) ───
const nodeActiveSize = 24;
const fontActiveSize = 15;
const edgeActiveWidth = '4px';
const arrowActiveScale = 1.2;
// ─── activate 시 색상 ───
const nodeActiveColor = '#6466f1'; // 선택한 노드
const fromColor = '#ff6348'; // 후수지식
const toColor = '#1e90ff'; // 선수지식

// 학교급 3색(초=green / 중=violet / 고=magenta) × 명도 3단계.
// 전부 흰 배경 비텍스트 대비 ≥3:1 (WCAG 1.4.11). 노란색 등 저대비 색 퇴출.
// 색 진실원천 1곳 — 범례(뷰 template)도 이 값을 바인딩한다.
const GRADE_COLORS = {
    // 초등 (green)  3.34 / 5.13 / 7.87
    초1: '#4f9d57',
    초2: '#4f9d57',
    초3: '#2e7d32',
    초4: '#2e7d32',
    초5: '#1b5e20',
    초6: '#1b5e20',
    // 중등 (violet) 5.21 / 8.02 / 10.24
    중1: '#7e57c2',
    중2: '#5e35b1',
    중3: '#4527a0',
    // 고등 (magenta) 4.62 / 6.40 / 9.05
    수학: '#d6336c',
    수1: '#b3215a',
    수2: '#b3215a',
    미적: '#8a1a47',
    확통: '#8a1a47',
    기하: '#8a1a47'
};
const DEFAULT_NODE_COLOR = 'gray';

// 학년(conceptGradeLevel) → 노드 색상
const getNodeColor = (gradeLevel) => GRADE_COLORS[gradeLevel] ?? DEFAULT_NODE_COLOR;

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
        spacing: 40
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

        // 선택 상태(클릭으로 유지). 호버는 선택이 없을 때만 미리보기 — 모바일/터치 대응.
        let selectedEl = null;
        const applyFocus = (el) => {
            setDimStyle(cy, {
                'background-color': dimColor,
                'line-color': dimColor,
                'source-arrow-color': dimColor,
                color: dimColor
            });
            setFocus(el, fromColor, toColor, edgeActiveWidth, arrowActiveScale);
        };

        // 노드 탭(클릭) = 선택. focus 를 유지하고 id 를 상세보기로 전달.
        cy.on('tap', 'node', (e) => {
            selectedEl = e.target;
            applyFocus(selectedEl);
            if (opts.onTapNode) {
                opts.onTapNode(e.target.id());
            }
        });
        // 빈 배경 탭 = 선택 해제.
        cy.on('tap', (e) => {
            if (e.target === cy) {
                selectedEl = null;
                setResetFocus(cy);
            }
        });
        // 데스크톱 호버 미리보기 — 선택이 있으면 덮어쓰지 않음.
        cy.on('mouseover', 'node', (e) => {
            if (!selectedEl) {
                applyFocus(e.target);
            }
        });
        cy.on('mouseout', 'node', () => {
            if (!selectedEl) {
                setResetFocus(cy);
            }
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

    return { initGraph, destroy, getNodeColor, GRADE_COLORS };
}
