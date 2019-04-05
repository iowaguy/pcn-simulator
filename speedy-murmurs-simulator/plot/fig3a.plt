#!/usr/bin/gnuplot -persist
set title "Figure 3b"
set xlabel "Epoch Number"
set ylabel "Count"
set xrange [0:700]
set yrange [0:25000]
set pointsize 1
unset logscale y
plot "plot/fig3a.txt" using (column(0)):2:xtic(100) with points title "Transactions","plot/fig3a.txt" using (column(0)):3:xtic(100) with points title "Set Link"
