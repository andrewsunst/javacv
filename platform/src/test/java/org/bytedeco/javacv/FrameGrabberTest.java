/*
 * Copyright (C) 2016-2017 Samuel Audet
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bytedeco.javacv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.junit.Test;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.junit.Assert.*;

/**
 * Test cases for FrameGrabber classes. Also uses other classes from JavaCV.
 *
 * @author Samuel Audet
 */
public class FrameGrabberTest {

    @Test public void testFFmpegFrameGrabber() {
        System.out.println("FFmpegFrameGrabber");

        File tempFile = new File(Loader.getTempDir(), "test.mkv");
        try {
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(new FileOutputStream(tempFile), 640, 480, 2);
            recorder.setFormat("matroska"); // mp4 doesn't support streaming
            recorder.setPixelFormat(AV_PIX_FMT_BGR24);
            recorder.setVideoCodecName("libx264rgb");
            recorder.setVideoQuality(0); // lossless
            recorder.setSampleFormat(AV_SAMPLE_FMT_S16);
            recorder.setSampleRate(44100);
            recorder.setAudioCodecName("pcm_s16le");
            recorder.start();

            Frame[] frames = new Frame[1000];
            for (int n = 0; n < frames.length; n++) {
                Frame frame = new Frame(640, 480, Frame.DEPTH_UBYTE, 3);
                UByteIndexer frameIdx = frame.createIndexer();
                for (int i = 0; i < frameIdx.rows(); i++) {
                    for (int j = 0; j < frameIdx.cols(); j++) {
                        for (int k = 0; k < frameIdx.channels(); k++) {
                            frameIdx.put(i, j, k, n + i + j + k);
                        }
                    }
                }
                recorder.record(frame);
                frames[n] = frame;
            }
            Frame audioFrame = new Frame();
            ShortBuffer audioBuffer = ShortBuffer.allocate(64 * 1024);
            audioFrame.sampleRate = 44100;
            audioFrame.audioChannels = 2;
            audioFrame.samples = new ShortBuffer[] {audioBuffer};
            for (int i = 0; i < audioBuffer.capacity(); i++) {
                audioBuffer.put(i, (short)i);
            }
            recorder.record(audioFrame);
            recorder.stop();
            recorder.release();

            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new FileInputStream(tempFile));
            grabber.setSampleMode(FrameGrabber.SampleMode.FLOAT);
            grabber.start();

            int n = 0, m = 0;
            Frame frame2;
            while ((frame2 = grabber.grab()) != null) {
                if (frame2.image != null) {
                    Frame frame = frames[n++];
                    assertEquals(frame.imageWidth, frame2.imageWidth);
                    assertEquals(frame.imageHeight, frame2.imageHeight);
                    assertEquals(frame.imageChannels, frame2.imageChannels);

                    UByteIndexer frameIdx = frame.createIndexer();
                    UByteIndexer frame2Idx = frame2.createIndexer();
                    for (int i = 0; i < frameIdx.rows(); i++) {
                        for (int j = 0; j < frameIdx.cols(); j++) {
                            for (int k = 0; k < frameIdx.channels(); k++) {
                                int b = frameIdx.get(i, j, k);
                                assertEquals(b, frame2Idx.get(i, j, k));
                            }
                        }
                    }
                } else {
                    FloatBuffer audioBuffer2 = (FloatBuffer)frame2.samples[0];
                    while (audioBuffer2.hasRemaining()) {
                        assertEquals((float)audioBuffer.get(m++) / (Short.MAX_VALUE + 1), audioBuffer2.get(), 0);
                    }
                }
            }
            assertEquals(frames.length, n);
            assertEquals(null, grabber.grab());
            grabber.restart();
            grabber.stop();
            grabber.release();
        } catch (Exception e) {
            fail("Exception should not have been thrown: " + e);
        } finally {
            tempFile.delete();
        }
    }

    @Test
    public void testFFmpegFrameGrabberLockingTest() {
        final boolean[] failed = {false};
        final int numberOfInstances = 20;
        System.out.println("FFmpegFrameGrabberLocking");

        Runnable[] runables = new Runnable[numberOfInstances];
        Thread[] threads = new Thread[numberOfInstances];
        final boolean[] finish = new boolean[numberOfInstances];
        for (int instance = 0; instance < numberOfInstances; instance++) {
            final int instance_final = instance;
            Runnable r = new Runnable() {
                public void run() {

                    File tempFile = new File(Loader.getTempDir(), "test" + instance_final + ".mkv");
                    try {
                        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(new FileOutputStream(tempFile), 640, 480, 2);
                        recorder.setFormat("matroska"); // mp4 doesn't support streaming
                        recorder.setPixelFormat(AV_PIX_FMT_BGR24);
                        recorder.setVideoCodecName("libx264rgb");
                        recorder.setVideoQuality(0); // lossless
                        recorder.setSampleFormat(AV_SAMPLE_FMT_S16);
                        recorder.setSampleRate(44100);
                        recorder.setAudioCodecName("pcm_s16le");
                        recorder.start();

                        Frame[] frames = new Frame[10];
                        for (int n = 0; n < frames.length; n++) {
                            Frame frame = new Frame(640, 480, Frame.DEPTH_UBYTE, 3);
                            UByteIndexer frameIdx = frame.createIndexer();
                            for (int i = 0; i < frameIdx.rows(); i++) {
                                for (int j = 0; j < frameIdx.cols(); j++) {
                                    for (int k = 0; k < frameIdx.channels(); k++) {
                                        frameIdx.put(i, j, k, n + i + j + k);
                                    }
                                }
                            }
                            recorder.record(frame);
                            frames[n] = frame;
                        }
                        Frame audioFrame = new Frame();
                        ShortBuffer audioBuffer = ShortBuffer.allocate(64 * 1024);
                        audioFrame.sampleRate = 44100;
                        audioFrame.audioChannels = 2;
                        audioFrame.samples = new ShortBuffer[] { audioBuffer };
                        for (int i = 0; i < audioBuffer.capacity(); i++) {
                            audioBuffer.put(i, (short) i);
                        }
                        recorder.record(audioFrame);
                        recorder.stop();
                        recorder.release();

                        Thread.sleep(1000);

                        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(new FileInputStream(tempFile));
                        grabber.setSampleMode(FrameGrabber.SampleMode.FLOAT);
                        grabber.start();

                        int n = 0, m = 0;
                        Frame frame2;
                        while ((frame2 = grabber.grab()) != null) {
                            if (frame2.image != null) {
                                Frame frame = frames[n++];
                                assertEquals(frame.imageWidth, frame2.imageWidth);
                                assertEquals(frame.imageHeight, frame2.imageHeight);
                                assertEquals(frame.imageChannels, frame2.imageChannels);

                                UByteIndexer frameIdx = frame.createIndexer();
                                UByteIndexer frame2Idx = frame2.createIndexer();
                                for (int i = 0; i < frameIdx.rows(); i++) {
                                    for (int j = 0; j < frameIdx.cols(); j++) {
                                        for (int k = 0; k < frameIdx.channels(); k++) {
                                            int b = frameIdx.get(i, j, k);
                                            assertEquals(b, frame2Idx.get(i, j, k));
                                        }
                                    }
                                }
                            } else {
                                FloatBuffer audioBuffer2 = (FloatBuffer) frame2.samples[0];
                                while (audioBuffer2.hasRemaining()) {
                                    assertEquals((float) audioBuffer.get(m++) / (Short.MAX_VALUE + 1),
                                            audioBuffer2.get(), 0);
                                }
                            }
                        }
                        assertEquals(frames.length, n);
                        assertEquals(null, grabber.grab());
                        grabber.restart();
                        grabber.stop();
                        grabber.release();
                    } catch (Error | Exception e) {
                        failed[0] = true;
                        fail("Exception should not have been thrown: " + e);
                    } finally {
                        tempFile.delete();
                        finish[instance_final] = true;
                    }
                }
            };

            runables[instance_final] = r;
        }

        for (int instance = 0; instance < numberOfInstances; instance++) {
            threads[instance] = new Thread(runables[instance]);
            threads[instance].setName("Testthread-" + instance);
        }
        
        for (int instance = 0; instance < numberOfInstances; instance++) {
            threads[instance].start();
        }
        
        while (true) {
            boolean finished = true;
            for (int instance = 0; instance < numberOfInstances; instance++) {
                if (!finish[instance]) {
                    finished = false;
                    break;
                }
            }
            
            if (!finished) {
                System.out.println("Still waiting...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        assertFalse(failed[0]);
    }

    @Test public void testFFmpegFrameGrabberSeeking() throws IOException {
        System.out.println("FFmpegFrameGrabberSeeking");

        File tempFile = new File(Loader.getTempDir(), "test.mp4");
        tempFile.deleteOnExit();

        FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(tempFile, 1024, 768, 2);
        recorder.setFormat("mp4");
        recorder.setFrameRate(30);
        recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
        recorder.setVideoCodec(AV_CODEC_ID_H264);
        recorder.setVideoQuality(10);
        recorder.setSampleRate(48000);
        recorder.setSampleFormat(AV_SAMPLE_FMT_FLTP);
        recorder.setAudioCodec(AV_CODEC_ID_AAC);
        recorder.setAudioQuality(0);
        recorder.start();

        for (int n = 0; n < 10000; n++) {
            Frame frame = new Frame(1024, 768, Frame.DEPTH_UBYTE, 3);
            UByteIndexer frameIdx = frame.createIndexer();
            for (int i = 0; i < frameIdx.rows(); i++) {
                for (int j = 0; j < frameIdx.cols(); j++) {
                    for (int k = 0; k < frameIdx.channels(); k++) {
                        frameIdx.put(i, j, k, n + i + j + k);
                    }
                }
            }
            recorder.record(frame);
        }
        Frame audioFrame = new Frame();
        ShortBuffer audioBuffer = ShortBuffer.allocate(48000 * 2 * 10000 / 30);
        audioFrame.sampleRate = 48000;
        audioFrame.audioChannels = 2;
        audioFrame.samples = new ShortBuffer[] {audioBuffer};
        for (int i = 0; i < audioBuffer.capacity(); i++) {
            audioBuffer.put(i, (short)i);
        }
        recorder.record(audioFrame);
        recorder.stop();
        recorder.release();

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(tempFile);
        grabber.start();
        int length = (int)grabber.getLengthInTime() - 1000000;

        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            long timestamp = random.nextInt(length);
            grabber.setTimestamp(timestamp);
            Frame frame = grabber.grab();
            long timestamp2 = grabber.getTimestamp();
            assertTrue(frame.image != null ^ frame.samples != null);
            System.out.println(timestamp2 + " - " + timestamp + " = " + (timestamp2 - timestamp));
            assertTrue(timestamp2 >= timestamp && timestamp2 < timestamp + 1000000);
        }
        grabber.stop();
    }
}
