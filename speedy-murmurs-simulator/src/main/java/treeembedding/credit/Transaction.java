package treeembedding.credit;

public class Transaction {

	int src;
	int dst;
	double val;
	double time;
	int requeue = 0;
	int mes = 0;
	int path = 0;
	
	public Transaction(double t, double v, int s, int d){
		this.src = s;
		this.dst = d;
		this.val = v;
		this.time = t;
	}
	
	public void incRequeue(double nexttime){
		this.requeue++;
		this.time = nexttime;
	}
	
	public void addPath(int p){
		this.path = this.path+p;
	}
	
	public void addMes(int p){
		this.mes = this.mes+p;
	}

}
