import { api } from "@/lib/api-client";

/**
 * Response from the pre-signed URL generation endpoint.
 */
export interface MediaPresignedUrlResponse {
  uploadUrl: string;
  key: string;
  publicUrl: string;
  expiresInMinutes: number;
}

/**
 * Options for uploading a single file via pre-signed URL.
 */
export interface PresignedUploadOptions {
  file: File;
  folder: string;
  onProgress?: (percent: number) => void;
}

/**
 * Result of a successful pre-signed upload.
 */
export interface PresignedUploadResult {
  url: string;
  key: string;
}

/**
 * Step 1: Request a pre-signed URL from the backend.
 */
async function getPresignedUrl(
  contentType: string,
  fileSize: number,
  folder: string
): Promise<MediaPresignedUrlResponse> {
  return api.post<MediaPresignedUrlResponse>("/media/presigned-url", null, {
    params: {
      fileName: "file",
      contentType,
      fileSize,
      folder,
    },
  });
}

/**
 * Step 2: Upload the file directly to S3 using the pre-signed URL.
 * Uses XMLHttpRequest for upload progress tracking.
 */
function uploadToS3(
  uploadUrl: string,
  file: File,
  onProgress?: (percent: number) => void
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", uploadUrl, true);
    xhr.setRequestHeader("Content-Type", file.type);

    if (xhr.upload && onProgress) {
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percent = Math.round((event.loaded / event.total) * 100);
          onProgress(percent);
        }
      };
    }

    xhr.onload = () => {
      if (xhr.status === 200) {
        resolve();
      } else {
        reject(new Error(`S3 upload failed with status: ${xhr.status}`));
      }
    };

    xhr.onerror = () => {
      reject(new Error("Network error during file upload to storage."));
    };

    xhr.send(file);
  });
}

/**
 * Step 3: Confirm the upload with the backend (PENDING → VERIFIED).
 */
async function confirmUpload(key: string): Promise<void> {
  await api.post<void>("/media/confirm", null, {
    params: { key },
  });
}

/**
 * Upload a single file using the full pre-signed URL flow:
 * 1. Get pre-signed URL from backend
 * 2. Upload file directly to S3
 * 3. Confirm upload with backend
 *
 * @returns The public URL and S3 key of the uploaded file
 */
export async function uploadFilePresigned(
  options: PresignedUploadOptions
): Promise<PresignedUploadResult> {
  const { file, folder, onProgress } = options;

  // Step 1: Get pre-signed URL
  const presigned = await getPresignedUrl(file.type, file.size, folder);

  // Step 2: Upload directly to S3
  await uploadToS3(presigned.uploadUrl, file, onProgress);

  // Step 3: Confirm with backend
  await confirmUpload(presigned.key);

  return {
    url: presigned.publicUrl,
    key: presigned.key,
  };
}

/**
 * Upload multiple files in parallel using pre-signed URLs.
 * Each file follows the full 3-step flow independently.
 *
 * @returns Array of results (url + key) for each successfully uploaded file
 */
export async function uploadFilesPresigned(
  files: File[],
  folder: string,
  onFileProgress?: (fileIndex: number, percent: number) => void
): Promise<PresignedUploadResult[]> {
  const uploadPromises = files.map((file, index) =>
    uploadFilePresigned({
      file,
      folder,
      onProgress: onFileProgress
        ? (percent) => onFileProgress(index, percent)
        : undefined,
    })
  );

  return Promise.all(uploadPromises);
}
