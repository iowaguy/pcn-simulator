#!/usr/bin/gnuplot -persist
set title "Figure 3b"
set xlabel "Epoch Number"
set ylabel "Stabilization"
set xrange [0:700]
set yrange [1:1e10]
set pointsize 1
set logscale y
plot "plot/fig3b.txt" using (column(0)):2:xtic(100) with points title "SilentWhispers","plot/fig3b.txt" using (column(0)):3:xtic(100) with points title "SpeedyMurmurs"
