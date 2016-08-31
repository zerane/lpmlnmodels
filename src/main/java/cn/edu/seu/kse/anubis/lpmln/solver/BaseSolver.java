package cn.edu.seu.kse.anubis.lpmln.solver;

import cn.edu.seu.kse.anubis.lpmln.model.SolverStats;
import cn.edu.seu.kse.anubis.lpmln.model.WeightedAnswerSet;
import cn.edu.seu.kse.anubis.util.CommandLineExecute;
import net.sf.json.JSONObject;
import net.sf.json.filters.MappingPropertyFilter;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by 王彬 on 2016/8/31.
 */
public class BaseSolver {
    protected List<WeightedAnswerSet> weightedAs=null;
    protected List<WeightedAnswerSet> maxWeightAs=null;
    protected String maxWeight;
    protected SolverStats stats;
    protected String executeProfile;
    protected String marginalTime;
    protected String maximalTime;
    protected List<Long> executeTime=new ArrayList<>();
    protected SimpleDateFormat sdf=new SimpleDateFormat("HH:mm:ss.SSSS");

    public List<WeightedAnswerSet> call(String cmd){
        Date enter=new Date();
        String[] cmdres= CommandLineExecute.callShellwithReturn(cmd,1);
        Date cmdExit=new Date();
        List<WeightedAnswerSet> was=solverResultProcess(cmdres[0]);
        weightedAs=was;
        stats=genSolverStatisticsInfo();
        Date exit=new Date();

        StringBuilder sb=new StringBuilder();

        sb.append("进入推理核心时间：").append(sdf.format(enter)).append(System.lineSeparator());
        sb.append("推理机核心调用结束：").append(sdf.format(cmdExit)).append(System.lineSeparator());
        sb.append("推理结果预处理完成：").append(sdf.format(exit)).append(System.lineSeparator());


        executeTime.add(exit.getTime()-enter.getTime());
//        executeTime.add(cmdExit.getTime()-enter.getTime());
//        executeTime.add(exit.getTime()-cmdExit.getTime());
        sb.append("推理核心用时：").append(executeTime.get(0)).append(" ms").append(System.lineSeparator());
        executeProfile=sb.toString();

        return was;
    }

    public List<WeightedAnswerSet> findMaxWeightedAs(){
        Date enter=new Date();
        int level2=0;
        int maxlevel1=0;
        maxWeightAs=new ArrayList<>();
        for(WeightedAnswerSet as:weightedAs){
            level2=as.getWeights().get(1);
            if(maxlevel1<as.getWeights().get(0)){
                maxlevel1=as.getWeights().get(0);
            }
        }

        for(WeightedAnswerSet as :weightedAs){
            if(as.getWeights().get(0) == maxlevel1){
                maxWeightAs.add(as);
            }
        }
        maxWeight=""+level2+"*alpha + "+maxlevel1;
        Date exit=new Date();
        StringBuilder sb=new StringBuilder();
        sb.append("求最大权重可能世界用时：").append(exit.getTime()-enter.getTime()).append(" ms");
        sb.append(System.lineSeparator());
        maximalTime=sb.toString();
        return maxWeightAs;
    }

    public String marginalDistribution(int factor){
        Date enter=new Date();
        HashMap<String,Double> result=new HashMap<>();
        double wsum=0;
        double expw=0;
        for(WeightedAnswerSet as:weightedAs){
            expw= Math.exp(as.getWeights().get(0)*1.0/factor);
            wsum+=expw;
            as.setProbability(expw);
        }

        for(WeightedAnswerSet as:weightedAs){
            expw=as.getProbability();
            as.setProbability(expw/wsum);
        }

        HashSet<String> literals=null;
        for(WeightedAnswerSet as : weightedAs){
            expw=as.getProbability();
            literals=as.getAnswerSet().getLiterals();
            for(String lit:literals){
                if(result.containsKey(lit)){
                    wsum=result.get(lit);
                }else {
                    wsum=0;
                }
                wsum+=expw;
                result.put(lit,wsum);
            }
        }

        String res= formateMarginalResult(result);
        Date exit=new Date();
        StringBuilder sb=new StringBuilder();
        sb.append("求边缘分布用时：").append(exit.getTime()-enter.getTime()).append(" ms");
        sb.append(System.lineSeparator());
        marginalTime=sb.toString();
        return res;
    }

    public String formateMarginalResult(HashMap<String, Double> result){
        StringBuilder fres=new StringBuilder();
        for(HashMap.Entry<String,Double> entry:result.entrySet()){
            fres.append(entry.getKey()).append("  ").append(entry.getValue()).append(System.lineSeparator());
        }
        return fres.toString();
    }

    public SolverStats genSolverStatisticsInfo(){
        SolverStats sta = new SolverStats();

        return sta;
    }

    public String getExecuteProfile() {
        return executeProfile;
    }

    public void setExecuteProfile(String executeProfile) {
        this.executeProfile = executeProfile;
    }

    public List<Long> getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(List<Long> executeTime) {
        this.executeTime = executeTime;
    }

    public List<WeightedAnswerSet> solverResultProcess(String result){
        return null;
    }

    public List<WeightedAnswerSet> getMaxWeightAs() {
        return maxWeightAs;
    }

    public void setMaxWeightAs(List<WeightedAnswerSet> maxWeightAs) {
        this.maxWeightAs = maxWeightAs;
    }

    public String getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(String maxWeight) {
        this.maxWeight = maxWeight;
    }

    public SolverStats getStats() {
        return stats;
    }

    public void setStats(SolverStats stats) {
        this.stats = stats;
    }

    public String getMarginalTime() {
        return marginalTime;
    }

    public void setMarginalTime(String marginalTime) {
        this.marginalTime = marginalTime;
    }

    public String getMaximalTime() {
        return maximalTime;
    }

    public void setMaximalTime(String maximalTime) {
        this.maximalTime = maximalTime;
    }
}