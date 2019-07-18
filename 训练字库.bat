@echo off
TITLE Tesseract字库训练 
CHCP 65001
color 3f
mode con cols=60 lines=40
setlocal enabledelayedexpansion
set name=fzlantingkanhei
:home
cls
ECHO. ===========================================================	  		 				  
echo     请按照提示输入参数,乱输入概不负责,没有做判断哦! 			  
echo     请按照提示输入参数,乱输入概不负责,没有做判断哦! 			  
echo     请按照提示输入参数,乱输入概不负责,没有做判断哦! 		   						  				  
echo			1.全部功能
echo			2.聚集并打包
echo			3.生成box
echo			4.生成字体文件
echo			5.生成tr文件
echo			6.生成inttemp,pffmtable,shapetable文件
echo			7.通过txt生成box,tif文件
ECHO. ===========================================================
set tool=
set /p tool=请输入功能：
echo.
echo.
echo.
echo 字库名字已经自动设置为%name%,如要修改请编辑本文件name变量!		
ECHO. -----------------------------------------------------------
if %tool% == 2 goto to_cntraining
if %tool% == 3 goto to_box
if %tool% == 4 goto to_font
if %tool% == 5 goto to_tr
if %tool% == 6 goto to_charset
if %tool% == 7 goto text2image
:to_font
ECHO. -----------------------------------------------------------
echo.
echo  			1.普通字体
echo  			2.斜体  
echo  			3.黑体  
echo  			4.不知道fixed是什么字体╮(╯▽╰)╭ 
echo  			5.下划线体  
echo  			6.不知道fraktur是什么体╮(╯▽╰)╭  
echo            fontname italic bold fixed serif fraktur
echo.					 
ECHO. -----------------------------------------------------------
set font=
set /p font=请输入字体选项：
ECHO. -----------------------------------------------------------

if %font% == 1 echo font 0 0 0 0 0 > %name%.font_properties
if %font% == 2 echo font 1 0 0 0 0 > %name%.font_properties
if %font% == 3 echo font 0 1 0 0 0 > %name%.font_properties
echo ****生成字体文件 "font_properties"结束按回车开始生成box****
pause >nul
if %tool% == 4 goto home
set dobox=
set /p dobox=是否需要重新生成box文件(y/n)?：
if %dobox% == n goto passbox
:to_box
echo 匹配字库是指需要用哪个字库去识别当前训练的文字
set trainLib=
set /p trainLib=请输入匹配字库名(例如:chenyang)：
ECHO. -----------------------------------------------------------
echo. 					
echo.注意:请确定tif文件已生成,并且tif文件命名跟你输入的字库名,编码名相同
echo.  			才能正确生成box,否则会创建失败 
echo.				   按任意键继续
ECHO. -----------------------------------------------------------
for /f "delims=" %%i in ('dir /b *.tif') do (
set boxNames= %%i
echo tesseract !boxNames! !boxNames:~0,-4! -l %trainLib% batch.nochop makebox
tesseract !boxNames! !boxNames:~0,-4! -l %trainLib% batch.nochop makebox
)
:passbox
echo **********生成字体文件结束..按回车开始创建tr文件************
echo. 
pause >nul
if %tool% == 3 goto home
:to_tr
echo 开始创建tr文件...
for /f "delims=" %%i in ('dir /b *.tif') do (
set trNames= %%i
echo tesseract !trNames! !trNames:~0,-4! -l %trainLib% batch.nochop makebox
tesseract !trNames! !trNames:~0,-4!  nobatch box.train
)
echo ************训练结束..按回车开始创建unicharset**************
echo. 
pause>nul
:to_charset
echo 提取字符,创建unicharset和tr..
for /f "delims=" %%i in ('dir /b *.box') do (
set charsetName=!charsetName! %%i
)
echo %charsetName%
unicharset_extractor %charsetName%
echo ********创建unicharset结束..按回车开始聚集Clustering********
echo. 
pause >nul
:to_cntraining
echo 边界微调mftraining..
for /f "delims=" %%i in ('dir /b *.tr') do (
set ClusterNames=!ClusterNames! %%i
)
mftraining -F %name%.font_properties -U unicharset -O  %name%.unicharset !ClusterNames!
echo ********边界微调结束,按回车开始聚集Clustering***************
pause>nul
echo 聚集Clustering..
cntraining !ClusterNames!
echo ********聚集Clustering结束,按回车开始重命名文件*************
echo. 
pause>nul
echo 重命名文件..
rename normproto %name%.normproto
rename inttemp %name%.inttemp
rename pffmtable %name%.pffmtable
rename shapetable %name%.shapetable 
echo ************重命名文件结束..按回车开始生成字库**************
echo. 
echo. 
pause>nul
echo 生成字库..
combine_tessdata %name%.
echo **************生成字库结束,按任意键退出*********************
echo.  
pause>nul
if %tool% == 7 goto text2image
:text2image
for /f "delims=" %%i in ('dir /b *.txt') do (
set textname= %%i
echo tesseract !trNames! 
text2image --text=%%i --outputbase="test.font.exp"!textname:~0,-4! --fontconfig_tmpdir="%temp%" --ptsize=12 --font="Source Han Sans CN" --fonts_dir="C:\Windows\Fonts"
)

