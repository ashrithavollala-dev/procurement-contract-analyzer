import { z } from 'zod';
import { MAX_UPLOAD_BYTES } from '../../config';

/** Client-side validation mirroring the backend's checks (the server re-validates everything). */
export const uploadSchema = z.object({
  file: z
    .instanceof(File, { message: 'Choose a PDF file' })
    .refine((file) => file.size > 0, 'The file is empty')
    .refine(
      (file) => file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf'),
      'Only PDF files are supported',
    )
    .refine((file) => file.size <= MAX_UPLOAD_BYTES, `The file must be at most ${Math.round(MAX_UPLOAD_BYTES / 1024 / 1024)} MB`),
  title: z.string().trim().max(255, 'At most 255 characters').optional(),
  vendorName: z.string().trim().max(255, 'At most 255 characters').optional(),
});

export type UploadForm = z.infer<typeof uploadSchema>;
