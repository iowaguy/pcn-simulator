#!/usr/bin/gnuplot -persist

set title "Fig 2b"
set xlabel "Trees"
set ylabel "(Hop)Delay"
set grid
plot "fig2b_vals.txt" u (column(0)):2:xtic(1) w l title "","fig2b_vals.txt" u (column(0)):3:xtic(1) w l title ""

