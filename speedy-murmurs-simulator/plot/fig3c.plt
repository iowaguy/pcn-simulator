#!/usr/bin/gnuplot -persist
set title "Figure 3c"
set xlabel "Epoch Number"
set ylabel "Success Ratio"
set xrange [0:700]
set yrange [0:1]
set pointsize 1
set ytic 0.2
unset logscale y
plot "plot/fig3c.txt" using (column(0)):2:xtic(100) with lines title "SilentWhispers","plot/fig3c.txt" using (column(0)):3:xtic(100) with lines title "SpeedyMurmurs"
