#! /bin/bash
#AUBO ROBOT 程序启动脚本
#author:cdj

#***********************************************************************
#函数功能: 获取当前脚本所在的绝对路径  
#参数说明: 1个参数   arg1:执行该程序时的命令行第一个参数
#
#说   明: 
#***********************************************************************
get_this_exec_file_path(){
	
	dirname $0|grep "^/" >/dev/null
	
	if [ $? -eq 0 ];then
		this_dir=`dirname $0`
	else
		dirname $0|grep "^\." >/dev/null
		retval=$?
		if [ $retval -eq 0 ];then
			this_dir=`dirname $0|sed "s#^.#$this_dir#"`
		else
			this_dir=`dirname $0|sed "s#^#$this_dir/#"`
		fi
	fi
	
	this_dir=${this_dir}"/"
	
	if [ $? = "1" ] ;then  
        return 1  
    else  
        return 0  
    fi
}


#初始化 this_dir 变量
this_dir=`pwd`
get_this_exec_file_path $0


cd /root/AuboRobotWorkSpace/Safety_Point/
python ./robotcontrol.py &

dateStr="robotcontrol.py启动：-`date +%y-%m-%d-%H-%M-%S`"
echo "$dateStr" >> "$this_dir/log.txt"

exit 0
