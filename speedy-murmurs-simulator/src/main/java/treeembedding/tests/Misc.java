package treeembedding.tests;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class Misc {
	
	public static int[] selectRoots(String file, boolean random, int trees, int seed){
		try{ 
			int[] roots = new int[trees];
		    BufferedReader br = new BufferedReader(new FileReader(file));
		    String line;
		    int a = 0;
		    Random rand = new Random(seed);
		    while ((line =br.readLine()) != null){
		    	String[] parts = line.split(" ");
		    	int[] cur = new int[parts.length];
		    	for (int j = 0; j < cur.length; j++){
		    		cur[j] = Integer.parseInt(parts[j]);
		    		if (random){
			    		if (cur[j] > a){
			    			a = cur[j];
			    		}
			    	} else {
			    		if (a + cur.length - j <= trees){
			    			roots[a] = cur[j];
			    			a++;
			    		}
			    	}
		    	}
		    	if (!random && a + cur.length > trees){
		    		Set<Integer> set = new HashSet<Integer>();
		    		while (a < trees){
		    			int index = rand.nextInt(cur.length);
		    			if (!set.contains(index)){
		    				roots[a] = cur[index];
		    				set.add(index);
		    				a++;
		    			}
		    		}
		    		 br.close();
		    		return roots;
		    	}
		    }
		    if (random){
		    	for (int i = 0; i < roots.length; i++){
		    		roots[i] = rand.nextInt(a);
		    	}
		    	 br.close();
		    	return roots;
		    }
		   
		}catch (IOException e){
			e.printStackTrace();
		}
		return null;
	}

}
