## A template Makefile to produce a small report.

## Toos that we need

LATEX  = pdflatex
EPS2PDF = epstopdf
GNUPLOT = gnuplot

## Name of the final report

REPORT = report.pdf

SOURCE = report.tex

DIAGRAMS = results1.pdf	results2.pdf

## Rules 

all:  $(DIAGRAMS) $(REPORT)

$(REPORT) : $(SOURCE)
	$(LATEX) $(SOURCE);
	$(LATEX) $(SOURCE)

%.eps : %.plot 
	$(GNUPLOT) $<

%.pdf : %.eps
	$(EPS2PDF)  $<

clean :
	rm -f *.pdf *.eps *.aux *.log *.out *.*~

