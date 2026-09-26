using System;
using System.IO;
using System.Threading;
using NAudio.Wave;

namespace Sound2ArtNet.WasapiLoopback
{
    class Program
    {
        static void Main(string[] args)
        {
            try
            {
                using var capture = new WasapiLoopbackCapture();
                var stdout = Console.OpenStandardOutput();
                var waveFormat = capture.WaveFormat;

                // Informiere den aufrufenden Java-Prozess über das Format
                Console.Error.WriteLine($"WASAPI_READY:{waveFormat.SampleRate}:{waveFormat.Channels}:{waveFormat.BitsPerSample}");
                Console.Error.Flush();

                int bytesPerSample = waveFormat.BitsPerSample / 8;
                int channels = waveFormat.Channels;
                int blockAlign = waveFormat.BlockAlign;

                byte[] monoBuffer = new byte[8192];

                capture.DataAvailable += (s, e) =>
                {
                    if (e.BytesRecorded <= 0) return;

                    int frames = e.BytesRecorded / blockAlign;
                    int requiredBytes = frames * 2;
                    if (monoBuffer.Length < requiredBytes)
                    {
                        monoBuffer = new byte[requiredBytes * 2];
                    }

                    int outIdx = 0;
                    bool isFloat = waveFormat.Encoding == WaveFormatEncoding.IeeeFloat || waveFormat.BitsPerSample == 32;

                    for (int i = 0; i < e.BytesRecorded; i += blockAlign)
                    {
                        float mono;
                        if (isFloat)
                        {
                            float left = BitConverter.ToSingle(e.Buffer, i);
                            float right = (channels > 1) ? BitConverter.ToSingle(e.Buffer, i + 4) : left;
                            mono = (left + right) * 0.5f;
                        }
                        else
                        {
                            // 16-bit PCM
                            short left = (short)((e.Buffer[i] & 0xFF) | (e.Buffer[i + 1] << 8));
                            short right = (channels > 1) ? (short)((e.Buffer[i + 2] & 0xFF) | (e.Buffer[i + 3] << 8)) : left;
                            mono = ((left + right) * 0.5f) / 32768.0f;
                        }

                        // Clamp und in 16-bit PCM Mono umwandeln
                        short pcm16 = (short)Math.Clamp((int)(mono * 32767.0f), -32768, 32767);
                        monoBuffer[outIdx++] = (byte)(pcm16 & 0xFF);
                        monoBuffer[outIdx++] = (byte)((pcm16 >> 8) & 0xFF);
                    }

                    stdout.Write(monoBuffer, 0, outIdx);
                    stdout.Flush();
                };

                capture.RecordingStopped += (s, e) =>
                {
                    if (e.Exception != null)
                    {
                        Console.Error.WriteLine($"WASAPI_ERROR:{e.Exception.Message}");
                    }
                };

                capture.StartRecording();

                // Lese von stdin. Sobald stdin geschlossen wird (Java Prozess beendet/Stream geschlossen), beende die Aufnahme.
                while (Console.In.Read() != -1)
                {
                    Thread.Sleep(200);
                }

                capture.StopRecording();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"WASAPI_FATAL:{ex.Message}");
                Console.Error.Flush();
            }
        }
    }
}
