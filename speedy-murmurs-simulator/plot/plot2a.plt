#!/usr/bin/gnuplot -persist

set title "Fig 2a"
set xlabel "Trees"
set ylabel "Success Ratio"
set grid
plot "fig2a_vals.txt" u (column(0)):2:xtic(1) w l title "","fig2a_vals.txt" u (column(0)):3:xtic(1) w l title ""

