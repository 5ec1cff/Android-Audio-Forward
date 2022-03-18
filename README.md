# Android 音频转发

参考 [sndcpy](https://github.com/rom1v/sndcpy) 做的一个 Android 音频转发工具，不过使用了 AudioRecord 捕获 REMOTE_SUBMIX 输出，以实现捕获系统音频。

**需要 root 权限（或者其他能够捕获 REMOTE_SUBMIX 的权限）。**

**需要手机和接收端在同一个局域网**

# 构建

直接在 Android Studio 中构建 apk （但通过 app_process 执行而非安装到系统）

# 启动 server

将构建好的 apk push 到设备（假设它位于 `/path/to/apk.apk`），在任意 root shell （如 `adb shell -t su`）下执行：

```shell
/system/bin/app_process -Djava.class.path=/path/to/apk.apk / five.ec1cff.audiostreamer.AudioStream -f 4
```

此时会监听网络特定端口（默认 51415），等待 client 连接，输出为 raw PCM 格式。 

参数说明：

|参数|说明|
|--|--|
|`-p`|端口，默认 `51415`|
|`-f`|格式（`AudioFormat.ENCODING_PCM_*`），默认 `AudioFormat.ENCODING_PCM_FLOAT`|
|`-ar`|采样率，默认 `48000`|
|`-c`|频道数，默认 `2` (`CHANNEL_IN_STEREO`)|

# client 连接

受限于作者水平，暂时无法写出接收端。

可以使用 [ffplay](https://www.ffmpeg.org/download.html) （以下参数请根据启动 server 的参数自行调整）：

```shell
ffplay -ar 48000 -f f32le -channels 2 tcp://your.phone:51415
```

# 延迟

个人测试发现，似乎用 ffplay 播放一开始会有延迟（两秒左右），但是右键 ffplay 的播放窗口即可很好地同步。 

# 未来展望

1. 实现接收端  
2. 支持音视频同传  

# 参考

[rom1v/sndcpy: Android audio forwarding (scrcpy, but for audio)](https://github.com/rom1v/sndcpy)

[android remote submix 录制系统内置的声音 - pengxinglove - 博客园](https://www.cnblogs.com/pengxinglove/p/5469448.html)
