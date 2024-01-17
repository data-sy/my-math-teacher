import html2pdf from 'html2pdf.js';

export function useHtmlToPdf() {
  const htmlToPdf = (location, fileName) => {
    // const date = new Date().toISOString().replace(/[-T:\.Z]/g, '');
    // const filename = `${fileName}_${date}.pdf`;
    const filename = `${fileName}.pdf`;

    html2pdf()
      .set({
        margin: [5, 5, 5, 5],
        filename,
        pagebreak: { mode: 'avoid-all' },
        image: { type: 'jpeg', quality: 1 },
        html2canvas: {
          useCORS: true,
          scrollY: 0,
          scale: 1,
          dpi: 300,
          letterRendering: true,
          allowTaint: false,
        },
        jsPDF: { orientation: 'portrait', unit: 'mm', format: 'a4' },
      })
      .from(location)
      .save();
  };

  return { htmlToPdf };
}
